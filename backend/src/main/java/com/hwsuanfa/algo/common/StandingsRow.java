package com.hwsuanfa.algo.common;

/**
 * 从平台官方榜单抓下来的一条选手成绩。
 * 我们不做 OJ、不判题，榜单数据要么抓、要么手填，站内排名是在此之上算出来的。
 */
public record StandingsRow(
        String handle,
        String officialRank,
        int acCount,
        // Integer：AtCoder 的 history/kenkoooo 都不给罚时与用时，允许 null（显示为「—」）
        Integer penaltyMinutes,
        Integer durationSeconds) {
}
