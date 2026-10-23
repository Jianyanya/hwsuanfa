package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目元数据回填（站主）：为缺 difficulty/tags 的题目补数据。
 *
 * - Codeforces：官方 problemset.problems 一次拉全量，按 contestId+index 匹配，
 *   rating 定难度档、官方 tags 翻译成常用中文入库。
 * - 洛谷：服务端逐题读取题目页，_feInjection 取难度、HTML tag span 取标签（限流 700ms/题）。
 * - AtCoder：无轻量公开源，暂不回填。
 *
 * 实现注：解析全用字符串切分而非正则——嵌套转义在生成代码时太容易写坏。
 */
@RestController
@RequestMapping("/api/admin/problems")
@RequiredArgsConstructor
public class AdminProblemController {

    private final ProblemMapper problemMapper;

    /** 洛谷登录 Cookie（环境变量 LUOGU_COOKIE 或 yml app.luogu.cookie）；
     *  洛谷题目页现在要求登录，无 Cookie 会被 302 重定向到登录页。 */
    @org.springframework.beans.factory.annotation.Value("${app.luogu.cookie:}")
    private String luoguCookie;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String[] LUOGU_DIFF = {
            "暂无评定", "入门", "普及−", "普及", "普及+/提高-", "提高",
            "提高+/省选−", "省选/NOI−", "NOI/NOI+/CTSC",
    };

    private static String cfBucket(int rating) {
        int lo = rating / 100 * 100;
        return lo + "-" + (lo + 99);
    }

    private static final Map<String, String> CF_TAG_ZH = Map.ofEntries(
            Map.entry("dp", "动态规划"), Map.entry("graphs", "图论"),
            Map.entry("greedy", "贪心"), Map.entry("math", "数学"),
            Map.entry("data structures", "数据结构"), Map.entry("trees", "树"),
            Map.entry("strings", "字符串"), Map.entry("number theory", "数论"),
            Map.entry("combinatorics", "组合数学"), Map.entry("binary search", "二分"),
            Map.entry("constructive algorithms", "构造"), Map.entry("dfs and similar", "DFS"),
            Map.entry("sorting", "排序"), Map.entry("brute force", "暴力"),
            Map.entry("implementation", "模拟"), Map.entry("geometry", "几何"),
            Map.entry("bitmasks", "位运算"), Map.entry("two pointers", "双指针"),
            Map.entry("hashing", "哈希"), Map.entry("probabilities", "概率"),
            Map.entry("shortest paths", "最短路"), Map.entry("games", "博弈"));

    /** 回填（CF 全量 + 洛谷一批）；返回本次更新数、失败数与剩余数 */
    @PostMapping("/backfill-metadata")
    public ApiResult<Map<String, Object>> backfill(
            @RequestParam(defaultValue = "40") int limit,
            @AuthenticationPrincipal LoginUser me) {
        if (!me.isSiteOwner()) throw new BizException(ErrorCode.FORBIDDEN);

        int updated = 0;
        int failed = 0;
        int skipped = 0;

        // ---- Codeforces：官方接口一次全量 ----
        List<Problem> cfPending = problemMapper.selectList(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPlatform, "CODEFORCES")
                .and(w -> w.isNull(Problem::getDifficulty)
                        .or().isNull(Problem::getTags)
                        .or().eq(Problem::getTags, "")));
        if (!cfPending.isEmpty()) {
            Map<String, String> cfRaw = fetchCfProblemset();   // key -> rating|tags
            for (Problem p : cfPending) {
                String key = cfKey(p.getProblemNo());
                if (key == null) continue;
                String raw = cfRaw.get(key);
                if (raw == null) continue;
                // raw 格式：rating|tag1,tag2
                String[] parts = raw.split("\\|", 2);
                int rating = Integer.parseInt(parts[0]);
                Problem patch = new Problem();
                patch.setId(p.getId());
                boolean changed = false;
                if (p.getDifficulty() == null || p.getDifficulty().isBlank()) {
                    patch.setDifficulty(cfBucket(rating));
                    changed = true;
                }
                if ((p.getTags() == null || p.getTags().isBlank()) && parts.length > 1 && !parts[1].isBlank()) {
                    patch.setTags(parts[1]);
                    changed = true;
                }
                if (changed) {
                    problemMapper.updateById(patch);
                    updated++;
                }
            }
        }

        // ---- 洛谷：逐题爬（限流）----
        List<Problem> luoguPending = problemMapper.selectList(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPlatform, "LUOGU")
                .and(w -> w.isNull(Problem::getDifficulty)
                        .or().isNull(Problem::getTags)
                        .or().eq(Problem::getTags, ""))
                .last("LIMIT " + Math.max(1, Math.min(limit, 60))));
        for (Problem p : luoguPending) {
            try {
                String[] meta = fetchLuoguMeta(p.getProblemNo());
                if (meta != null) {
                    Problem patch = new Problem();
                    patch.setId(p.getId());
                    boolean changed = false;
                    if ((p.getDifficulty() == null || p.getDifficulty().isBlank()) && hasText(meta[0])) {
                        patch.setDifficulty(meta[0]);
                        changed = true;
                    }
                    if ((p.getTags() == null || p.getTags().isBlank()) && hasText(meta[1])) {
                        patch.setTags(meta[1]);
                        changed = true;
                    }
                    if (changed) {
                        problemMapper.updateById(patch);
                        updated++;
                    } else {
                        skipped++;
                    }
                } else {
                    skipped++;
                }
                Thread.sleep(700);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                failed++;
            }
        }

        Long remaining = problemMapper.selectCount(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPlatform, "LUOGU")
                .and(w -> w.isNull(Problem::getDifficulty)
                        .or().isNull(Problem::getTags)
                        .or().eq(Problem::getTags, "")));
        return ApiResult.ok(Map.of("updated", updated, "failed", failed, "skipped", skipped,
                "remaining", remaining));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 题号 → "contestId:index"（题号格式如 102860B / 816B） */
    private static String cfKey(String problemNo) {
        if (problemNo == null || problemNo.isBlank()) return null;
        int i = 0;
        while (i < problemNo.length() && Character.isDigit(problemNo.charAt(i))) i++;
        if (i == 0 || i == problemNo.length()) return null;
        return problemNo.substring(0, i) + ":" + problemNo.substring(i).toUpperCase();
    }

    /**
     * CF problemset.problems 全量解析。
     * 返回 key -> "rating|tag1,tag2"；解析用字符串切分，不用正则。
     */
    private Map<String, String> fetchCfProblemset() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(
                            "https://codeforces.com/api/problemset.problems"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return Map.of();
            String body = resp.body();
            // 只取 problemRows 部分，避开 statistics
            int rowsStart = body.indexOf("\"problemResults\"");
            if (rowsStart > 0) body = body.substring(0, rowsStart);

            String marker = "\"contestId\":";
            Map<String, String> out = new LinkedHashMap<>();
            int idx = body.indexOf(marker);
            while (idx >= 0) {
                int next = body.indexOf(marker, idx + marker.length());
                String chunk = body.substring(idx, next < 0 ? body.length() : next);
                String contestId = readNumeric(chunk, 0);
                String index = readStringField(chunk, "\"index\":\"");
                String ratingStr = readNumericAfter(chunk, "\"rating\":");
                String tags = readTags(chunk);
                if (contestId != null && index != null && ratingStr != null && tags != null) {
                    out.put(contestId + ":" + index.toUpperCase(), ratingStr + "|" + tags);
                }
                idx = next;
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 从 pos 起读连续数字 */
    private static String readNumeric(String s, int pos) {
        int i = pos;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        return i > pos ? s.substring(pos, i) : null;
    }

    private static String readNumericAfter(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;
        return readNumeric(s, i + key.length());
    }

    /** 读 "index":"XXX" 形式的字符串字段值 */
    private static String readStringField(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        int end = s.indexOf('"', start);
        return end < 0 ? null : s.substring(start, end);
    }

    /** 读 "tags":["a","b"] 里的标签，翻译成中文常用名 */
    private static String readTags(String chunk) {
        int i = chunk.indexOf("\"tags\":[");
        if (i < 0) return null;
        int start = i + 8;
        int end = chunk.indexOf("]", start);
        if (end < 0) return null;
        String inner = chunk.substring(start, end);
        if (inner.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String raw : inner.split(",")) {
            String t = raw.replace("\"", "").trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(CF_TAG_ZH.getOrDefault(t, t));
        }
        return sb.toString();
    }

    /** 洛谷单题：_feInjection 取难度，HTML tag span 取标签 */
    private String[] fetchLuoguMeta(String pid) throws Exception {
        String url = "https://www.luogu.com.cn/problem/"
                + URLEncoder.encode(pid, StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", luoguCookie == null ? "" : luoguCookie);
        HttpRequest req = builder.GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            // 302/403 = 未登录或 Cookie 过期：计入失败并在消息里说明原因
            throw new IllegalStateException("洛谷题目页 HTTP " + resp.statusCode()
                    + (resp.statusCode() == 302 || resp.statusCode() == 403
                       ? "（洛谷要求登录：请配置 app.luogu.cookie 后重试）" : ""));
        }
        String html = resp.body();

        String difficulty = null;
        String feKey = "JSON.parse(decodeURIComponent(\"";
        int fe = html.indexOf(feKey);
        if (fe >= 0) {
            int start = fe + feKey.length();
            int end = html.indexOf("\"))", start);
            if (end > start) {
                try {
                    String json = java.net.URLDecoder.decode(
                            html.substring(start, end), StandardCharsets.UTF_8);
                    String diffKey = "\"difficulty\":";
                    int di = json.indexOf(diffKey);
                    if (di >= 0) {
                        String num = readNumeric(json, di + diffKey.length());
                        if (num != null) {
                            int code = Integer.parseInt(num);
                            if (code >= 1 && code < LUOGU_DIFF.length) difficulty = LUOGU_DIFF[code];
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        StringBuilder tags = new StringBuilder();
        String tagKey = "class=\"tag\"";
        int ti = html.indexOf(tagKey);
        while (ti >= 0) {
            int gt = html.indexOf('>', ti);
            int lt = html.indexOf('<', gt);
            if (gt < 0 || lt < 0) break;
            String t = html.substring(gt + 1, lt).trim();
            if (!t.isEmpty()) {
                if (tags.length() > 0) tags.append(',');
                tags.append(t);
            }
            ti = html.indexOf(tagKey, lt);
        }

        if (difficulty == null && tags.length() == 0) return null;
        return new String[]{difficulty, tags.length() > 0 ? tags.toString() : null};
    }
}
