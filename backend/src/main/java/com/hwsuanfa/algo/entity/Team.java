package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 队伍。
 * leaderUserId 是「一队一队长」的唯一权威来源，
 * team_member 表负责「队长必须是本队成员」。
 */
@Data
@TableName("team")
public class Team {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;

    /** 当前队长，可为 null（建队后需先指定） */
    private Long leaderUserId;

    /** 成员上限，当前固定 3 */
    private Integer maxMembers;

    /** ACTIVE / FROZEN */
    private String status;

    private String frozenReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
