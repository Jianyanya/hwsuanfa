package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建/更新题目 */
@Data
public class ProblemRequest {

    @NotBlank(message = "请填写题目名称")
    @Size(max = 200, message = "题目名称过长")
    private String title;

    /** CODEFORCES / ATCODER / NOWCODER / LEETCODE / LUOGU / OTHER */
    @NotBlank(message = "请选择题目来源平台")
    private String platform;

    @Size(max = 64, message = "题号过长")
    private String problemNo;

    @Size(max = 500, message = "链接过长")
    private String url;

    @Size(max = 32, message = "难度过长")
    private String difficulty;

    @Size(max = 255, message = "标签过长")
    private String tags;

    @Size(max = 500, message = "备注过长")
    private String note;
}
