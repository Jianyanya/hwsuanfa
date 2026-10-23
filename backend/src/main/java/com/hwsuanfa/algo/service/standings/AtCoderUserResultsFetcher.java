package com.hwsuanfa.algo.service.standings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwsuanfa.algo.common.StandingsRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * AtCoder 单人成绩抓取（2026-09-28 重写：旧数据源已失效）。
 *
 * ⚠️ 数据源变化：AtCoder 改版后 users/{id}/results/json 已下线（实测 404）。
 * 现在的成绩来源是 **history/json**（公开、无需登录）：
 *   - 每行含 ContestScreenName（⚠️ 格式是 "abc477.contest.atcoder.jp" 带后缀）、
 *     Place（官方名次）、IsRated
 *   - ⚠️ history 只含 **rated** 比赛；unrated 比赛查不到名次
 * AC 数从 kenkoooo 公开镜像的提交列表按比赛过滤统计（与提交同步同源）。
 *
 * 拿不到：罚时/用时（两个数据源都不给），由队长手动补。
 *
 * ⚠️ AtCoder 的整场榜单 JSON（/contests/{id}/standings/json）通常要登录，
 *    所以走不了「整场抓取」路线，这是它与 Codeforces 的本质差异。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AtCoderUserResultsFetcher implements UserResultsFetcher {

    private static final String HISTORY_URL = "https://atcoder.jp/users/%s/history/json";
    private static final String KENKO_URL = "https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions";

    /** 最多翻 10 页 × 1000 条，覆盖一个学生一年的提交量 */
    private static final int MAX_PAGES = 10;

    private final ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public boolean supports(String platform) {
        return "ATCODER".equalsIgnoreCase(platform);
    }

    @Override
    public StandingsRow fetchUserResult(String externalId, String handle) {
        if (externalId == null || externalId.isBlank() || handle == null || handle.isBlank()) {
            return null;
        }
        JsonNode historyRow = findHistoryRow(externalId, handle);
        if (historyRow == null) {
            // history 只含 rated 比赛：没参加 / unrated 都会走到这里
            log.info("AtCoder history 中无 {} 的 {} 记录（可能未参加或 unrated）", handle, externalId);
            return null;
        }
        int place = historyRow.path("Place").asInt(0);
        int ac = countAcInContest(externalId, handle);
        log.info("AtCoder 抓到成绩：{} @ {} → 第 {} 名，AC {}", handle, externalId, place, ac);
        return new StandingsRow(handle, String.valueOf(place), ac, null, null);
    }

    /** 在 history/json 里找该比赛的行；ContestScreenName 带 ".contest.atcoder.jp" 后缀，归一化后比对 */
    private JsonNode findHistoryRow(String externalId, String handle) {
        String url = String.format(HISTORY_URL,
                URLEncoder.encode(handle, StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("AtCoder history 请求失败：HTTP {}，{}", resp.statusCode(), url);
                return null;
            }
            JsonNode arr = objectMapper.readTree(resp.body());
            if (!arr.isArray()) {
                return null;
            }
            for (JsonNode row : arr) {
                if (normalize(row.path("ContestScreenName").asText(""))
                        .equals(externalId.toLowerCase())) {
                    return row;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("AtCoder history 读取异常：{}", e.getMessage());
            return null;
        }
    }

    /** 从 kenkoooo 提交列表统计该比赛的 AC 题数（不同题去重） */
    private int countAcInContest(String externalId, String handle) {
        Set<String> solved = new HashSet<>();
        long cursor = 1462986000L;
        try {
            for (int page = 0; page < MAX_PAGES; page++) {
                String url = KENKO_URL + "?user="
                        + URLEncoder.encode(handle, StandardCharsets.UTF_8)
                        + "&from_second=" + cursor;
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    break;
                }
                JsonNode arr = objectMapper.readTree(resp.body());
                if (!arr.isArray() || arr.isEmpty()) {
                    break;
                }
                for (JsonNode row : arr) {
                    long ts = row.path("epoch_second").asLong();
                    if (externalId.equalsIgnoreCase(row.path("contest_id").asText(""))
                            && "AC".equals(row.path("result").asText())) {
                        solved.add(row.path("problem_id").asText(""));
                    }
                    cursor = Math.max(cursor, ts + 1);
                }
                if (arr.size() < 1000) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("kenkoooo AC 统计失败：{}", e.getMessage());
        }
        return solved.size();
    }

    /** "abc477.contest.atcoder.jp" → "abc477" */
    private String normalize(String screenName) {
        String s = screenName.toLowerCase();
        int dot = s.indexOf('.');
        return dot > 0 ? s.substring(0, dot) : s;
    }
}
