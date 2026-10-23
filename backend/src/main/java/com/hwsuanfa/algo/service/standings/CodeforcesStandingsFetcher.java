package com.hwsuanfa.algo.service.standings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.common.StandingsRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Codeforces 官方榜单抓取（官方公开 API contest.standings，无需登录）。
 * 每个选手的：官方名次、AC 数（type=FINAL 且得分>0）、罚时（分钟）、
 * 用时（解出题目里最晚的一次提交时刻，秒）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeforcesStandingsFetcher implements StandingsFetcher {

    /**
     * ⚠️ CF 这个接口有个坑：对非管理员只允许 **contestId 一个参数**，
     * 多加 showUnofficial / from / count 任何参数都会返回
     * 「Non-gym contest standings for non-admin users are available only via
     *   anonymous GET requests with no extra parameters」。
     * 代价是大比赛的返回体可能十几 MB（我们只按 handle 匹配自己人，其余丢弃）。
     */
    private static final String API = "https://codeforces.com/api/contest.standings?contestId=%s";

    private final ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public boolean supports(String platform) {
        return "CODEFORCES".equalsIgnoreCase(platform);
    }

    @Override
    public List<StandingsRow> fetch(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "该比赛没有填写平台比赛编号，无法抓取榜单");
        }
        String url = String.format(API, externalId.trim());
        try {
            // 实测 Div.3 全量榜单 7.9MB / 31s，超时给足
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw BizException.of(ErrorCode.BAD_REQUEST, "Codeforces 接口返回 " + resp.statusCode());
            }

            JsonNode root = objectMapper.readTree(resp.body());
            if (!"OK".equals(root.path("status").asText())) {
                throw BizException.of(ErrorCode.BAD_REQUEST, "Codeforces 接口返回异常状态");
            }
            List<StandingsRow> out = new ArrayList<>();
            for (JsonNode row : root.path("result").path("rows")) {
                JsonNode party = row.path("party");
                // ⚠️ CF 的 party 结构是 members[].handle（不是 handles[]），
                //    队伍参赛时另有 party.teamName。搞错字段会静默丢掉全部行。
                String teamName = party.path("teamName").asText(null);
                String handle = teamName != null && !teamName.isBlank()
                        ? teamName
                        : party.path("members").path(0).path("handle").asText(null);
                if (handle == null || handle.isBlank()) {
                    continue;
                }
                int ac = 0;
                int duration = 0;
                for (JsonNode pr : row.path("problemResults")) {
                    if ("FINAL".equals(pr.path("type").asText()) && pr.path("points").asDouble() > 0) {
                        ac++;
                        int t = pr.path("bestSubmissionTimeSeconds").asInt(0);
                        if (t > duration) duration = t;
                    }
                }
                out.add(new StandingsRow(handle, String.valueOf(row.path("rank").asInt()),
                        ac, row.path("penalty").asInt(0), duration));
            }
            log.info("CF 榜单抓取成功：contest={}，{} 名选手", externalId, out.size());
            return out;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "抓取 Codeforces 榜单失败：" + e.getMessage());
        }
    }
}
