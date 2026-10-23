package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题单。
 *
 * ⚠️ 只存「这份训练包含哪些题」的引用（见 problem_list_item），
 *    不存题面、不判题 —— 我们不是 OJ。
 */
@Data
@TableName("problem_list")
public class ProblemList {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;

    /** PERSONAL / TEAM */
    private String scope;

    /** scope=TEAM 时必填 */
    private Long teamId;

    private Long ownerId;

    /** PENDING / APPROVED / REJECTED —— 队员提交的队伍题单先进待审核 */
    private String auditStatus;

    private Long auditedBy;
    private LocalDateTime auditedAt;
    private String rejectReason;

    private Long lastEditorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
