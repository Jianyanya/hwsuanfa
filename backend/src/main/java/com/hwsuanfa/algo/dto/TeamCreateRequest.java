package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeamCreateRequest {

    @NotBlank(message = "请填写队伍名称")
    @Size(max = 64, message = "队伍名称不能超过 64 字")
    private String name;

    @Size(max = 500, message = "简介不能超过 500 字")
    private String description;

    /**
     * 首任队长。建队时可以留空，之后再由站主指定；
     * 但队伍一旦有成员，就必须有队长。
     */
    private Long leaderUserId;
}
