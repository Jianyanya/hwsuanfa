package com.hwsuanfa.algo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProblemMetadataImportRequest {

    @NotEmpty
    @Size(max = 500)
    private List<@Valid Item> records;

    @Data
    public static class Item {

        @NotBlank
        @Size(max = 64)
        private String problemNo;

        @Size(max = 200)
        private String title;

        @Size(max = 32)
        private String difficulty;

        @Size(max = 255)
        private String tags;

        @Size(max = 500)
        private String url;
    }
}
