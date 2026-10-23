package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水 —— **不可覆盖**。
 *
 * 撤销或修正一律通过反向事件（score 为负、reversed_event_id 指向原事件），
 * 不要 UPDATE 已有记录。
 * idempotent_key 上有唯一索引，是防重复发放的最后一道防线。
 */
@Data
@TableName("score_event")
public class ScoreEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String eventType;

    /**
     * 可正可负，负数为撤销/扣分。
     * ⚠️ 用 BigDecimal 而非 Integer：分值以后可能要出小数（如 3.5），
     *    列类型是 DECIMAL(8,2)，映射成 Integer 会丢精度。
     */
    private java.math.BigDecimal score;

    /** PROBLEM / ASSIGNMENT / CONTEST / PROBLEM_LIST */
    private String bizType;
    private Long bizId;

    private String reason;

    /** 如 first_ac:12:345 / daily:2026-09-20:12 / assignment:7:12 */
    private String idempotentKey;

    private Long reversedEventId;

    private Long operatorId;

    private LocalDateTime occurredAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
