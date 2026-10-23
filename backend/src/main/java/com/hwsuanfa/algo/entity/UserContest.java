package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人参赛记录 —— 与队伍参赛（team_contest）是两条独立的线。
 *
 * 规则（用户定的）：
 *   - 队伍参加了这场比赛，队内成员**不能**再以个人身份参加
 *   - 成员先以个人参加了，队长**仍然可以**再给队伍报名；
 *     但记排名时该成员不进队伍名单，个人成绩独立展示
 */
@Data
@TableName("user_contest")
public class UserContest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long contestId;

    /** 选手在平台上的 handle，抓官方榜单时匹配用 */
    private String externalHandle;

    /** JOINED / CANCELLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
