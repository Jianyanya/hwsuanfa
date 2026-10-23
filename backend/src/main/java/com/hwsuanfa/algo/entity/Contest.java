package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 比赛 —— **手动登记**，不从平台上抓取。
 *
 * 「我们队要参加哪场比赛」是教学决策，全球每天几百场比赛，
 * 抓来的列表毫无意义。题目本体与排名永远在 OJ 上，这里只存引用与结果。
 */
@Data
@TableName("contest")
public class Contest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** CODEFORCES / ATCODER / NOWCODER / LEETCODE / LUOGU / OTHER */
    private String platform;

    private String url;

    /** 比赛在平台上的真实 id（如 CF /contest/1932 → 1932），抓官方榜单用 */
    private String externalId;

    /** S / A / B / C / OTHER —— 决定参赛积分档位 */
    private String level;

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
