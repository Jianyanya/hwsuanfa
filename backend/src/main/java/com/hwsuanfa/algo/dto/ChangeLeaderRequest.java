package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeLeaderRequest {

    @NotNull(message = "请指定新队长")
    private Long newLeaderId;
}
