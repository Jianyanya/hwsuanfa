package com.hwsuanfa.algo.service.standings;

import com.hwsuanfa.algo.common.StandingsRow;

/**
 * 「按用户抓成绩」的抓取器 —— 和整场榜单（StandingsFetcher）互补。
 *
 * 为什么需要两种：CF 能给整场公开榜单（一次拿全部人）；
 * AtCoder 的整场榜单 JSON 要登录，但**每个用户自己的参赛结果**是公开的
 * （/users/{id}/results/json），所以 AtCoder 只能按人抓、逐个匹配。
 */
public interface UserResultsFetcher {

    boolean supports(String platform);

    /**
     * 抓某选手在指定比赛里的成绩。
     *
     * @param externalId 比赛在平台上的 id（如 AtCoder 的 abc300）
     * @param handle     选手在平台上的用户名
     * @return 有参赛记录则返回成绩；没有参加/查不到返回 null
     */
    StandingsRow fetchUserResult(String externalId, String handle);
}
