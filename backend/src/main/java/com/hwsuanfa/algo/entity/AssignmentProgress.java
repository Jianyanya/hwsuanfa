package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业完成进度（用户级）。
 * (assignment_id, user_id) 唯一。
 */
@Data
@TableName("assignment_progress")
public class AssignmentProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assignmentId;
    private Long userId;

    /** 完成时所属队伍，便于按队统计 */
    private Long teamId;

    /** IN_PROGRESS / COMPLETED */
    private String status;

    /** 是否逾期完成：完成时刻与 due_at 比较后落库，避免每次重算 */
    private Boolean late;

    private Integer completedCount;
    private Integer totalCount;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
