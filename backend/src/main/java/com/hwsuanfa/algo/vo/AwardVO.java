package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AwardVO {

    private Long id;
    private Long userId;
    private String userNickname;
    private String awardName;
    private String contestName;
    private String awardLevel;
    private LocalDate awardedAt;
    private String certUrl;
    private Boolean certificateAvailable;
    private String note;
    private Boolean isPublic;
    private String auditStatus;
    private String rejectReason;
    private LocalDateTime createdAt;
}
