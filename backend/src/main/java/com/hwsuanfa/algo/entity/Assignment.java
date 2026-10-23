package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 作业 / 每日一题（同一张表，用 type 区分）。
 *
 * ⚠️ 表里**没有** status 列 —— 状态一律由后端按服务器时间实时计算，
 *    存下来会导致「时间到了但状态没变」的不一致。
 */
@Data
@TableName("assignment")
public class Assignment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** TEAM（指定队伍）/ GLOBAL（全体）/ DAILY（每日一题） */
    private String type;

    private String title;
    private String description;

    /** 关联题单（可选，仅作来源留痕；作业的题目以 assignment_problem 为准） */
    private Long problemListId;

    /** 每日一题关联的单题 */
    private Long problemId;

    private LocalDateTime startAt;
    private LocalDateTime dueAt;

    /** 完成奖励，与积分列同类型 */
    private java.math.BigDecimal scoreReward;

    /** 逾期完成是否仍给分 */
    private Boolean allowLateReward;

    /** 每日一题专用：发布日，同一天只能有一条 */
    private LocalDate publishDate;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
