package com.hwsuanfa.algo.service.standings;

import com.hwsuanfa.algo.common.StandingsRow;

import java.util.List;

/**
 * 官方榜单抓取器（按平台实现）。
 *
 * 现实约束：Codeforces 有官方公开 API，可以真自动；
 * AtCoder / 洛谷 / 牛客没有公开接口，需要登录态且违反平台约定 → 回落手动录入。
 */
public interface StandingsFetcher {

    boolean supports(String platform);

    /** 抓榜单；拿不到时抛业务异常，由上层回落手动录入 */
    List<StandingsRow> fetch(String externalId);
}
