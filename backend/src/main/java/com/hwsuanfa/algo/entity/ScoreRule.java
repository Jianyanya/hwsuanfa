package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分规则。站主可调分值。
 * (event_type, sub_key) 唯一；sub_key 用空串而非 NULL，因为唯一索引里 NULL 不参与去重。
 */
@Data
@TableName("score_rule")
public class ScoreRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventType;
    private String label;
    /** 与 score_event.score 同类型，支持小数分值 */
    private java.math.BigDecimal score;

    /** 子键，如比赛等级 S/A/B；无子键时为空串 */
    private String subKey;

    private Boolean enabled;
    private String description;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
