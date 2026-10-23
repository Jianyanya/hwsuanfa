package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 队伍参赛选择。(team_id, contest_id) 唯一；取消用 status=CANCELLED 而不是删行，留痕 */
@Data
@TableName("team_contest")
public class TeamContest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;
    private Long contestId;

    private Long selectedBy;

    /** SELECTED / CANCELLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
