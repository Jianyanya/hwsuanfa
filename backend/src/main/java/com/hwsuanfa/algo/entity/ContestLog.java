package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 比赛日志 —— 一支队伍一场比赛一条。
 *
 * member_ids 存 JSON 数组：参赛成员人数少、只读展示，不做关联查询，够用。
 * status：队员提交进 PENDING，队长（或站主）确认后 CONFIRMED 并发名次分。
 */
@Data
@TableName("contest_log")
public class ContestLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contestId;

    /** TEAM / USER —— 一条成绩对应一个参赛主体 */
    private String participantType;

    /** 个人参赛时的选手 */
    private Long userId;

    private Long teamId;

    /** 参赛成员 id 的 JSON 数组，如 [3,4,5] */
    private String memberIds;

    /** 名次，如 "12/230" */
    private String rankResult;

    private Integer solvedCount;

    /** 通过题数（抓取或手填） */
    private Integer acCount;

    /** 比赛用时秒 */
    private Integer durationSeconds;

    /** 罚时分钟 */
    private Integer penaltyMinutes;

    private String summary;

    /** 赛后关联题单（可选） */
    private Long problemListId;

    /** PENDING / CONFIRMED */
    private String status;

    private Long createdBy;
    private Long confirmedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
