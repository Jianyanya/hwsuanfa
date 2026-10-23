package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 登记 / 编辑比赛（站主）。比赛是手动登记的，不从平台抓取 */
@Data
public class ContestRequest {

    @NotBlank(message = "请填写比赛名称")
    @Size(max = 200, message = "名称不能超过 200 字")
    private String name;

    @NotBlank(message = "请选择比赛平台")
    private String platform;

    @Size(max = 500, message = "链接过长")
    private String url;

    /** S / A / B / C / OTHER —— 决定参赛积分档位 */
    @NotBlank(message = "请选择比赛等级")
    private String level;

    private LocalDateTime startAt;

    private LocalDateTime endAt;
}
