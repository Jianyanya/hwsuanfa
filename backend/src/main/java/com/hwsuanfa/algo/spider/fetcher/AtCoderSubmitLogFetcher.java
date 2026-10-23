package com.hwsuanfa.algo.spider.fetcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.spider.SubmitLog;
import com.hwsuanfa.algo.spider.SubmitLogFetcher;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * AtCoder 提交记录爬虫。
 *
 * AtCoder 官方没有公开的个人提交 API（官方的 standings JSON 要登录），
 * 实际做法是走 kenkoooo 的公开镜像库（AtCoder Problems 背后的那个），
 * 它按时间窗（1000 条/次）提供任意用户的完整提交列表 —— 业界标准做法，
 * 无需登录、无需 Cookie。缺点是多了一个第三方依赖。
 * 2026-09-28 起存全部提交（AC/WA/TLE…），不只 AC。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AtCoderSubmitLogFetcher implements SubmitLogFetcher {

    private static final String API = "https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions";

    private static final int MAX_PAGES = 500;
    private static final long FIRST_EPOCH_SECOND = 1462986000L;

    private final ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String platform() {
        return "ATCODER";
    }

    @Override
    public List<SubmitLog> fetchAcLogs(String handle) {
        return fetchLogs(handle, null);
    }

    @Override
    public List<SubmitLog> fetchLogs(String handle, LocalDateTime since) {
        List<SubmitLog> out = new ArrayList<>();
        long cursor = since == null ? FIRST_EPOCH_SECOND
                : since.atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond();
        try {
            for (int page = 0; page < MAX_PAGES; page++) {
                String url = API + "?user="
                        + URLEncoder.encode(handle, StandardCharsets.UTF_8)
                        + "&from_second=" + cursor;
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 429) {
                    log.warn("kenkoooo 限流，已抓到 {} 条后停止", out.size());
                    break;
                }
                if (resp.statusCode() != 200) {
                    throw BizException.of(ErrorCode.BAD_REQUEST,
                            "AtCoder 镜像接口返回 " + resp.statusCode());
                }
                JsonNode arr = objectMapper.readTree(resp.body());
                if (!arr.isArray() || arr.isEmpty()) {
                    break;
                }
                long pageMaxTimestamp = cursor;
                for (JsonNode row : arr) {
                    if (row.path("id").asLong() == 0) {
                        continue;
                    }
                    long ts = row.path("epoch_second").asLong();
                    pageMaxTimestamp = Math.max(pageMaxTimestamp, ts);
                    long id = row.path("id").asLong();
                    String contestId = row.path("contest_id").asText("");
                    String problemId = row.path("problem_id").asText("");
                    LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneId.of("Asia/Shanghai"));
                    if (since != null && !time.isAfter(since)) continue;
                    out.add(new SubmitLog(platform(), problemId, time,
                            row.path("language").asText(null),
                            "https://atcoder.jp/contests/" + contestId + "/submissions/" + id,
                            row.path("result").asText("OTHER")));
                }
                if (pageMaxTimestamp <= cursor) break;
                cursor = pageMaxTimestamp + 1;
                if (arr.size() < 1000) {
                    break;
                }
                if (page + 1 < MAX_PAGES) Thread.sleep(120L);
            }
            log.info("AtCoder 提交记录抓取完成：{} 条 AC（handle={}）", out.size(), handle);
            return out;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AtCoder 提交记录抓取异常：{}", e.getMessage());
            throw BizException.of(ErrorCode.BAD_REQUEST, "抓取 AtCoder 提交记录失败：" + e.getMessage());
        }
    }
}
