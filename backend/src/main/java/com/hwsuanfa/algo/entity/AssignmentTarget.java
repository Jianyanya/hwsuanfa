package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业目标队伍 —— **发布时的队伍快照**。
 *
 * 全体作业发布时把当时所有有效队伍写进这张表；
 * 以后新增队伍**不会**被追溯进来，历史作业的范围也不会变化。
 * 这是计划书里的硬要求，不要改成「实时查全部队伍」。
 */
@Data
@TableName("assignment_target")
public class AssignmentTarget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assignmentId;
    private Long teamId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
