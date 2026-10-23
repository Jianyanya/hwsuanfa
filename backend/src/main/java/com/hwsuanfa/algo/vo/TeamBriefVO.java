package com.hwsuanfa.algo.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 站主后台的队伍列表项 —— 比详情轻，不含成员明细。
 */
@Data
@Builder
public class TeamBriefVO {
    private Long id;
    private String name;
    private String description;
    private Long leaderUserId;
    private String leaderNickname;
    private Integer maxMembers;
    private Integer memberCount;
    private String status;
    private String frozenReason;
    /** 站主后台用来显示「已满员」徽章 */
    private Boolean full;
}
