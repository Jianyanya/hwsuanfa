package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目。系统不提供独立题库入口，但题目实体是作业/题单/提交记录的基础数据。
 */
@Data
@TableName("problem")
public class Problem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** CODEFORCES / ATCODER / NOWCODER / LEETCODE / LUOGU / OTHER */
    private String platform;

    /** 平台内题号，如 1932F */
    private String problemNo;

    private String url;

    /** 难度标签（各平台口径不同，原样存） */
    private String difficulty;

    /** 逗号分隔 */
    private String tags;

    private String note;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
