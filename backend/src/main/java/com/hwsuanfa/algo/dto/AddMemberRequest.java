package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddMemberRequest {

    @NotNull(message = "请选择要加入的用户")
    private Long userId;

    /**
     * 是否同时指定为队长。
     * 队伍当前没有成员时，加入者会自动成为队长（否则会留下无队长的队伍）。
     */
    private Boolean asLeader;
}
