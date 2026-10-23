package com.hwsuanfa.algo.dto;

import lombok.Data;

/** 往题单里批量加入题目：既能给已有题目 id，也能直接粘文本 */
@Data
public class ProblemImportRequest {

    /** 已有题目 id */
    private java.util.List<Long> problemIds;

    /** 粘贴的文本（平台+题号、URL 混排都支持） */
    private String pasteText;

    /** 粘贴文本里没写平台时的默认平台 */
    private String defaultPlatform;

    /** 已存在的题号是否跳过（默认 true）。false 表示直接报错 */
    private Boolean skipDuplicates;
}
