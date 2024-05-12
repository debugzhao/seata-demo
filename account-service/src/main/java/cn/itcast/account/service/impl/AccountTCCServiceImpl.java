package cn.itcast.account.service.impl;

import cn.itcast.account.entity.AccountFreeze;
import cn.itcast.account.mapper.AccountFreezeMapper;
import cn.itcast.account.mapper.AccountMapper;
import cn.itcast.account.service.AccountTCCService;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author zhaojingchao
 * @Date 2024/05/11 20:28
 * @Email zhaojingchao@joysuch.com
 * @Desc
 */
@Slf4j
@Service
public class AccountTCCServiceImpl implements AccountTCCService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountFreezeMapper accountFreezeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(String userId, int money) {
        // 获取当前事务id
        String xid = RootContext.getXID();
        AccountFreeze oldAccountFreeze = accountFreezeMapper.selectById(xid);
        // 判断freeze表中是否有数据，如果有则说明已经执行过cancel，应该拒绝悬挂，直接返回
        if (oldAccountFreeze != null) {
            return;
        }

        // 1.扣减可用余额
        accountMapper.deduct(userId, money);
        // 2.记录冻结余额，事务状态
        AccountFreeze accountFreeze = new AccountFreeze();
        accountFreeze.setXid(xid);
        accountFreeze.setUserId(userId);
        accountFreeze.setFreezeMoney(money);
        accountFreeze.setState(AccountFreeze.State.TRY);
        accountFreezeMapper.insert(accountFreeze);
    }

    @Override
    public boolean confirm(BusinessActionContext ctx) {
        // 根据事务id删除冻结记录
        String xid = ctx.getXid();
        // 删除操作天生支持幂等性
        int result = accountFreezeMapper.deleteById(xid);
        return result == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(BusinessActionContext ctx) {
        String xid = ctx.getXid();
        String userId = (String)ctx.getActionContext("userId");
        AccountFreeze accountFreeze = accountFreezeMapper.selectById(xid);
        // 空回滚判断，如果accountFreeze == null，说明没有执行try操作，需要进行空回滚
        if (accountFreeze == null) {
            AccountFreeze rollbackAccountFreeze = new AccountFreeze();
            rollbackAccountFreeze.setXid(xid);
            rollbackAccountFreeze.setUserId(userId);
            rollbackAccountFreeze.setFreezeMoney(0);
            rollbackAccountFreeze.setState(AccountFreeze.State.CANCEL);
            accountFreezeMapper.insert(rollbackAccountFreeze);
            return true;
        }

        // 幂等判断，如果状态是CANCEL，说明已经执行过了，直接返回
        if (accountFreeze.getState() == AccountFreeze.State.CANCEL) {
            return true;
        }

        // 1.恢复可用金额
        accountMapper.refund(accountFreeze.getUserId(), accountFreeze.getFreezeMoney());

        // 2.将冻结金额清零，状态设置为取消
        accountFreeze.setFreezeMoney(0);
        accountFreeze.setState(AccountFreeze.State.CANCEL);
        int result = accountFreezeMapper.updateById(accountFreeze);
        return result == 1;
    }
}
