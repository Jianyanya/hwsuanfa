package com.hwsuanfa.algo.spider;

import java.time.LocalDateTime;

/**
 * 爬虫抓回来的一条提交记录。
 *
 * 2026-09-28 起存**全部提交**（AC/WA/TLE…），不只 AC —— 用户要求：
 * 热力图与近期动态要反映真实提交行为，AC 与否由 result 区分。
 */
public record SubmitLog(
        String platform,
        String problemNo,
        LocalDateTime submittedAt,
        String language,
        String url,
        String result) {

    /** 便利构造：AC 记录 */
    public static SubmitLog ac(String platform, String problemNo, LocalDateTime at,
                               String language, String url) {
        return new SubmitLog(platform, problemNo, at, language, url, "AC");
    }
}
