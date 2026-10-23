package com.hwsuanfa.algo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeamUpdateRequest {

    @Size(max = 64, message = "队伍名称不能超过 64 字")
    private String name;

    @Size(max = 500, message = "简介不能超过 500 字")
    private String description;
}
