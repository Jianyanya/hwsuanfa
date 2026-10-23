package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布 / 编辑作业。
 *
 * ⚠️ 全体作业（GLOBAL）**不接收 teamIds** —— 目标队伍由后端在发布那一刻
 *    取全部有效队伍做成快照，调用方传什么都不影响，避免「发布后才新增的队伍被算进去」。
 */
@Data
public class AssignmentRequest {

    /** TEAM / GLOBAL / DAILY */
    @NotBlank(message = "请选择作业类型")
    private String type;

    @NotBlank(message = "请填写作业标题")
    @Size(max = 200, message = "标题过长")
    private String title;

    @Size(max = 5000, message = "说明过长")
    private String description;

    /**
     * 作业包含的题目。每日一题传 1 个即可。
     * 允许为空 —— 只要给了 problemListId，题目会从题单里取。
     */
    private List<Long> problemIds;

    /**
     * 从题单导入题目。
     * 与 problemIds 会合并去重；两者都为空才报「至少需要一道题」。
     */
    private Long problemListId;

    /**
     * 目标队伍。type=TEAM 时必填；
     * type=GLOBAL 时忽略（后端自动快照全部有效队伍）；
     * type=DAILY 时留空表示面向所有人。
     */
    private List<Long> teamIds;

    @NotNull(message = "请填写开始时间")
    private LocalDateTime startAt;

    @NotNull(message = "请填写截止时间")
    private LocalDateTime dueAt;

    /** 支持小数（用 DECIMAL 存），但不能为负 */
    @DecimalMin(value = "0", message = "奖励积分不能为负")
    private java.math.BigDecimal scoreReward;

    /** 逾期完成是否仍给分，默认给 */
    private Boolean allowLateReward;

    /** 每日一题专用：发布日。留空则取服务器当天 */
    private LocalDate publishDate;
}
