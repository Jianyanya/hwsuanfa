package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 作业包含的题目 —— 「作业有哪些题」的唯一数据源 */
@Data
@TableName("assignment_problem")
public class AssignmentProblem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assignmentId;
    private Long problemId;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
