package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 题单 */
@Data
@Builder
public class ProblemListVO {

    private Long id;
    private String name;
    private String description;

    /** PERSONAL / TEAM */
    private String scope;
    private Long teamId;
    private String teamName;

    private Long ownerId;
    private String ownerNickname;
    private Long lastEditorId;

    /** PENDING / APPROVED / REJECTED */
    private String auditStatus;
    private String rejectReason;

    private Integer problemCount;

    /** 题目明细，列表页不带（省流量），详情页带 */
    private List<ProblemVO> problems;

    /** 当前访问者能否编辑 */
    private Boolean editable;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
