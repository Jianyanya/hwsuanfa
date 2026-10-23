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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Codeforces 提交记录爬虫。
 *
 * 数据源是 CF 官方公开 API（user.status）—— 不需要登录，也没有更好的抓法：
 * 能用官方接口拿到的数据，自己再解析一遍 HTML 纯属自找麻烦。
 * user.status 按 handle 列出该用户全部提交（含 verdict、题号、语言、时间）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeforcesSubmitLogFetcher implements SubmitLogFetcher {

    private static final String API = "https://codeforces.com/api/user.status?handle=%s&from=%d&count=%d";
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 100;

    private final ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String platform() {
        return "CODEFORCES";
    }

    @Override
    public List<SubmitLog> fetchAcLogs(String handle) {
        return fetchLogs(handle, null);
    }

    @Override
    public List<SubmitLog> fetchLogs(String handle, LocalDateTime since) {
        String encodedHandle = java.net.URLEncoder.encode(handle, java.nio.charset.StandardCharsets.UTF_8);
        List<SubmitLog> out = new ArrayList<>();
        try {
            boolean reachedCursor = false;
            for (int page = 0; page < MAX_PAGES && !reachedCursor; page++) {
                int from = page * PAGE_SIZE + 1;
                String url = String.format(API, encodedHandle, from, PAGE_SIZE);
                JsonNode root = request(url);
                JsonNode rows = root.path("result");
                if (!rows.isArray() || rows.isEmpty()) break;
                LocalDateTime oldest = null;
                for (JsonNode row : rows) {
                    long ts = row.path("creationTimeSeconds").asLong();
                    LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneId.of("Asia/Shanghai"));
                    if (oldest == null || time.isBefore(oldest)) oldest = time;
                    if (since != null && !time.isAfter(since)) continue;
                    String result = mapVerdict(row.path("verdict").asText(""));
                    if (result == null) continue;
                    JsonNode problem = row.path("problem");
                    long contestId = problem.path("contestId").asLong();
                    String index = problem.path("index").asText("");
                    if (contestId <= 0 || index.isEmpty()) continue;
                    String problemNo = contestId + index;
                    String url2 = "https://codeforces.com/contest/" + contestId + "/submission/" + row.path("id").asLong();
                    out.add(new SubmitLog(platform(), problemNo, time,
                            row.path("programmingLanguage").asText(null), url2, result));
                }
                if (since != null && oldest != null && !oldest.isAfter(since)) reachedCursor = true;
                if (rows.size() < PAGE_SIZE) break;
                if (page + 1 < MAX_PAGES) Thread.sleep(150L);
            }
            log.info("CF 提交记录抓取完成：{} 条 AC（handle={}）", out.size(), handle);
            return out;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("CF 提交记录抓取异常：{}", e.getMessage());
            throw BizException.of(ErrorCode.BAD_REQUEST, "抓取 Codeforces 提交记录失败：" + e.getMessage());
        }
    }

    private JsonNode request(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json").GET().build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw BizException.of(ErrorCode.BAD_REQUEST, "Codeforces 接口返回 " + resp.statusCode());
        JsonNode root = objectMapper.readTree(resp.body());
        if (!"OK".equals(root.path("status").asText())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "Codeforces 接口异常：" + root.path("comment").asText(""));
        }
        return root;
    }

    /** CF verdict → 站内 result 枚举；未知返回 null（跳过） */
    private String mapVerdict(String verdict) {
        return switch (verdict) {
            case "OK" -> "AC";
            case "WRONG_ANSWER" -> "WA";
            case "TIME_LIMIT_EXCEEDED" -> "TLE";
            case "MEMORY_LIMIT_EXCEEDED" -> "MLE";
            case "RUNTIME_ERROR" -> "RE";
            case "COMPILATION_ERROR" -> "CE";
            case "SKIPPED", "CHALLENGED", "PARTIAL" -> "OTHER";
            default -> null;
        };
    }
}
