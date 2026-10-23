package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 提交比赛日志（一场比赛一支队伍一条） */
@Data
public class ContestLogRequest {

    /** TEAM / USER —— 一条成绩对应一个参赛主体；不传默认 TEAM */
    private String participantType;

    /** 个人参赛时的选手（participantType=USER 时必填） */
    private Long userId;

    /** 队伍参赛时的队伍 */
    private Long teamId;

    /** 实际参赛成员（队伍参赛时必填；个人参赛不用） */
    private List<Long> memberIds;

    /** 名次，如 12/230 */
    @Size(max = 64, message = "名次过长")
    private String rankResult;

    @Min(0)
    private Integer solvedCount;

    /** 通过题数 */
    @Min(0)
    private Integer acCount;

    /** 比赛用时（秒） */
    @Min(0)
    private Integer durationSeconds;

    /** 罚时（分钟） */
    @Min(0)
    private Integer penaltyMinutes;

    @Size(max = 5000, message = "总结过长")
    private String summary;

    /** 赛后关联题单（可选） */
    private Long problemListId;
}
