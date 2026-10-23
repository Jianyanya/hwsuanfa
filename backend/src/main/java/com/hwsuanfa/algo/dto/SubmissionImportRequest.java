package com.hwsuanfa.algo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SubmissionImportRequest {

    @NotEmpty
    @Size(max = 5000)
    private List<@Valid Item> records;

    @Data
    public static class Item {

        @NotBlank
        @Size(max = 64)
        private String problemNo;

        @NotNull
        private LocalDateTime submittedAt;

        @Size(max = 32)
        private String language;

        @Size(max = 500)
        private String url;
    }
}
