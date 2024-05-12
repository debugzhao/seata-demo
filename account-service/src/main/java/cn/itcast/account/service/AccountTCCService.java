package cn.itcast.account.service;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * @Author zhaojingchao
 * @Date 2024/05/11 20:22
 * @Email zhaojingchao@joysuch.com
 * @Desc TCC模式实现分布式事务
 */
@LocalTCC
public interface AccountTCCService {

    /**
     * try操作：添加冻结金额，扣减可用金额
     * @param userId
     * @param money
     * @return
     */
    @TwoPhaseBusinessAction(name = "deduct", commitMethod = "confirm", rollbackMethod = "cancel")
    void deduct(@BusinessActionContextParameter(paramName = "userId") String userId,
               @BusinessActionContextParameter(paramName = "money") int money);


    /**
     * confirm操作：删除冻结金额
     * @param ctx
     * @return
     */
    boolean confirm(BusinessActionContext ctx);

    /**
     * cancel操作：删除冻结金额，恢复可用金额
     * @param ctx
     * @return
     */
    boolean cancel(BusinessActionContext ctx);
}
