package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 难度 → 分值对照表（按平台分别配置）。
 *
 * 为什么单独建表而不是写死在代码里：不同平台的难度口径完全不同
 * （洛谷按档位名、CF 按 rating 分段），而且分值属于**教学策略**，
 * 站主应当能自己调，调分值不该需要改代码重新部署。
 *
 * (platform, difficulty_key) 唯一；每个平台有一个 is_default 兜底档，
 * 题目标注的难度不在表里时用它的分值。
 */
@Data
@TableName("difficulty_score")
public class DifficultyScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** CODEFORCES / ATCODER / NOWCODER / LEETCODE / LUOGU / OTHER */
    private String platform;

    /** 该平台内的档位标识，如 "普及/提高−"、"1300-1699" */
    private String difficultyKey;

    /** 界面显示名 */
    private String label;

    /** 首次完成分值 */
    private BigDecimal score;

    /** 由易到难，下拉排序用 */
    private Integer sortOrder;

    /** 该平台的兜底档 */
    private Boolean isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
