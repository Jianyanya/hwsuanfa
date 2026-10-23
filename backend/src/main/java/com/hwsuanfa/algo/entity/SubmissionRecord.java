package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 做题 / 提交记录。
 *
 * ⚠️ (user_id, problem_id, is_first_ac) 上有唯一索引：
 *    同一道题只允许一条「首次 AC」记录，这是「同一道题不能重复拿首次完成积分」的
 *    数据库级保证。重复提交请把 is_first_ac 置 0。
 */
@Data
@TableName("submission_record")
public class SubmissionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long problemId;

    private String platform;
    private String problemNo;

    private String submissionUrl;
    private String codeContent;
    private String codeLanguage;

    /** AC / WA / TLE / MLE / RE / CE / OTHER */
    private String result;

    /** 首次有效完成标记，用于触发首次完成积分 */
    private Boolean isFirstAc;

    private LocalDateTime submittedAt;

    /** MANUAL / AUTO（洛谷只能 MANUAL） */
    private String source;

    private String syncBatchId;
    private Long verifiedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
