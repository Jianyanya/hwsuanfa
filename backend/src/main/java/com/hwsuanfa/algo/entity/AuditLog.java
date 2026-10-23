package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志。
 * operator_name 冗余存名字 —— 用户改名后历史记录仍可读。
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorId;
    private String operatorName;

    /** 如 TEAM_CREATE / LEADER_CHANGE / MEMBER_ADD ... */
    private String action;

    private String targetType;
    private Long targetId;

    /** 变更前后快照，JSON 字符串 */
    private String beforeJson;
    private String afterJson;

    private String detail;
    private String ip;

    /**
     * 只填 created_at —— audit_log 表**没有** updated_at 列。
     * ⚠️ 别在这里加 updatedAt，否则 MyBatis-Plus 生成的 INSERT 会带上不存在的列而报错。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
