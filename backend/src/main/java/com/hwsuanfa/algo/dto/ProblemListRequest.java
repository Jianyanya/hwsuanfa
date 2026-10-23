package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建 / 编辑题单 */
@Data
public class ProblemListRequest {

    @NotBlank(message = "请填写题单名称")
    @Size(max = 128, message = "名称不能超过 128 字")
    private String name;

    @Size(max = 500, message = "简介不能超过 500 字")
    private String description;

    /** PERSONAL / TEAM */
    @NotBlank(message = "请选择题单归属")
    private String scope;

    /** scope=TEAM 时必填 */
    private Long teamId;

    /** 创建时可以直接带一批已有题目 */
    private List<Long> problemIds;

    /**
     * 创建时直接粘贴一段文本批量导入题号。
     * 与 problemIds 可以同时给，最终合并去重。
     */
    private String pasteText;

    /** 粘贴文本里没写平台时的默认平台 */
    private String defaultPlatform;
}
