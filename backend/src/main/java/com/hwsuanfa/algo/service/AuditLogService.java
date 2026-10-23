package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.entity.AuditLog;
import com.hwsuanfa.algo.security.LoginUser;

public interface AuditLogService {

    /**
     * 记录一条操作日志。
     *
     * ⚠️ 这个方法**必须**在调用方的事务内执行：日志写失败要连同业务一起回滚，
     * 否则会出现「操作没成功但日志留了一条」的脏记录。
     */
    void record(LoginUser operator, String action, String targetType, Long targetId,
                Object before, Object after, String detail);

    default void record(LoginUser operator, String action, String targetType, Long targetId, String detail) {
        record(operator, action, targetType, targetId, null, null, detail);
    }

    /** 便于调用方直接构造 */
    AuditLog build(LoginUser operator, String action, String targetType, Long targetId,
                   Object before, Object after, String detail);
}
