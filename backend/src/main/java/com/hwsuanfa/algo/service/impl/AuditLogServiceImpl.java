package com.hwsuanfa.algo.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwsuanfa.algo.entity.AuditLog;
import com.hwsuanfa.algo.mapper.AuditLogMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void record(LoginUser operator, String action, String targetType, Long targetId,
                       Object before, Object after, String detail) {
        auditLogMapper.insert(build(operator, action, targetType, targetId, before, after, detail));
    }

    @Override
    public AuditLog build(LoginUser operator, String action, String targetType, Long targetId,
                          Object before, Object after, String detail) {
        AuditLog entity = new AuditLog();
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetail(detail);
        entity.setBeforeJson(toJson(before));
        entity.setAfterJson(toJson(after));

        if (operator != null) {
            entity.setOperatorId(operator.getId());
            // 冗余存名字：用户改名后历史记录仍可读
            entity.setOperatorName(operator.getNickname());
        }
        return entity;
    }

    /**
     * 序列化为 JSON 文本。
     *
     * ⚠️ 这里**不做**「传入 String 就原样返回」的优化 —— 那样一旦有人传了
     * 手写的 "k=v; k=v"，就会写进 MySQL 的 JSON 列并触发
     * `Data truncation: Invalid JSON text`（我们踩过这个坑）。
     * 统一交给 Jackson，普通字符串会被正规地序列化成带引号的 JSON 字符串。
     * 序列化失败不能让业务失败 —— 日志只是附属产物。
     */
    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.warn("审计日志序列化失败，降级为 JSON 字符串: {}", e.getMessage());
            try {
                return objectMapper.writeValueAsString(String.valueOf(o));
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
    }
}
