package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

/** 题目视图 */
@Data
@Builder
public class ProblemVO {
    private Long id;
    private String title;
    private String platform;
    private String problemNo;
    private String url;
    private String difficulty;
    private String tags;

    /** 当前访问者是否已完成（在作业详情里用） */
    private Boolean done;

    /** 该题按难度档位算出的首次完成分值，方便前端直接显示「这题几分」 */
    private java.math.BigDecimal score;
}
