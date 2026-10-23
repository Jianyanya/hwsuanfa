package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 提交获奖记录（队员） */
@Data
public class AwardRequest {

    @NotBlank(message = "请填写奖项名称")
    @Size(max = 128)
    private String awardName;

    @Size(max = 200)
    private String contestName;

    @Size(max = 32)
    private String awardLevel;

    private LocalDate awardedAt;

    @Size(max = 500)
    private String certUrl;

    @Size(max = 500)
    private String note;

    /** 是否公开到个人页，默认 true */
    private Boolean isPublic;
}
