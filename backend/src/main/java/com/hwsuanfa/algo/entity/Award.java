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
 * 获奖记录 —— 队员提交，站主审核后公示（is_public=true 才对他人可见）。
 */
@Data
@TableName("award")
public class Award {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String awardName;

    /** 所属比赛名称（选填） */
    private String contestName;

    /** 奖项等级：如 金牌 / 一等奖 / 省一（选填） */
    private String awardLevel;

    private LocalDate awardedAt;

    /** 证书外链（选填）；上传文件另存于 award_certificate。 */
    private String certUrl;

    private String note;

    /** 是否公开到个人页 */
    private Boolean isPublic;

    /** PENDING / APPROVED / REJECTED */
    private String auditStatus;

    private Long auditedBy;
    private LocalDateTime auditedAt;
    private String rejectReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
