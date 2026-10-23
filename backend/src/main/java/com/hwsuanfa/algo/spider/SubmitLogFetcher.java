package com.hwsuanfa.algo.spider;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 提交记录爬虫的平台插件接口。
 *
 * 实现一个新平台的步骤：
 *   1. 写一个类实现本接口
 *   2. 标 @Component 即自动注册进 SpiderRegistry（构造器里无需手动 Register）
 *   3. 用 UseCase.syncSubmissions() 验证
 *
 * 各平台现状（诚实标注，实现前先想清楚数据从哪来）：
 *   CODEFORCES — 官方公开 API（user.status），无需登录         ✅ 已接入
 *   ATCODER    — kenkoooo 公开镜像 API，无需登录               ✅ 已接入
 *   LUOGU      — 需要登录 Cookie，走 tools/luogu_sync.py 脚本手动同步  ❌ 插件未接入
 *   NOWCODER   — 提交历史需要登录态，暂未接入                    ❌
 *   LEETCODE   — 公开 GraphQL 只给最近 AC 列表，暂未接入          ❌
 *   QOJ        — 暂未接入                                       ❌
 */
public interface SubmitLogFetcher {

    /** 平台标识（与 problem.platform 一致） */
    String platform();

    /**
     * 抓某用户在该平台的 AC 提交记录。
     *
     * @param handle 用户在该平台的用户名（个人中心绑定的）
     * @return AC 记录列表，按时间升序或降序均可（落库前会排序）
     */
    List<SubmitLog> fetchAcLogs(String handle);

    /**
     * 增量抓取入口。平台适配器可以使用游标优化查询；旧适配器默认退化为全量抓取，
     * 由入库层按提交时间去重，保证新增平台不会破坏已有同步流程。
     */
    default List<SubmitLog> fetchLogs(String handle, LocalDateTime since) {
        return fetchAcLogs(handle);
    }
}
