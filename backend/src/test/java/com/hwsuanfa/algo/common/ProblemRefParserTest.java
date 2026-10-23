package com.hwsuanfa.algo.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 题号批量粘贴解析器单测。
 * 纯逻辑、无 Spring 上下文，跑得很快。
 */
class ProblemRefParserTest {

    private Map<String, String> asMap(ProblemRefParser.Result r) {
        return r.problems().stream().collect(Collectors.toMap(
                p -> p.platform() + ":" + p.problemNo(),
                ProblemRefParser.ParsedProblem::url,
                (a, b) -> a));
    }

    @Test
    @DisplayName("平台前缀 + 多个题号")
    void platformPrefixWithMultipleIds() {
        var r = ProblemRefParser.parse("CF 1932A 1932B 1932C", null);
        assertEquals(3, r.problems().size());
        assertTrue(asMap(r).containsKey("CODEFORCES:1932A"));
        assertTrue(asMap(r).containsKey("CODEFORCES:1932C"));
        assertEquals("https://codeforces.com/problemset/problem/1932/A", asMap(r).get("CODEFORCES:1932A"));
    }

    @Test
    @DisplayName("中文平台名 + 中文标点")
    void chinesePlatformAndPunctuation() {
        var r = ProblemRefParser.parse("洛谷 P1001，P1046、B2001；CF1001", null);
        var m = asMap(r);
        assertTrue(m.containsKey("LUOGU:P1001"), m.keySet().toString());
        assertTrue(m.containsKey("LUOGU:P1046"));
        assertTrue(m.containsKey("LUOGU:B2001"));
        // CF1001 是洛谷的题号（CF 系列），不是 codeforces 的 1001
        assertTrue(m.containsKey("LUOGU:CF1001"), m.keySet().toString());
        assertEquals("https://www.luogu.com.cn/problem/P1001", m.get("LUOGU:P1001"));
    }

    @Test
    @DisplayName("前缀紧贴题号：CF1932A / 洛谷P1001")
    void inlinePrefix() {
        var m = asMap(ProblemRefParser.parse("CF1932A", null));
        assertTrue(m.containsKey("CODEFORCES:1932A"), m.keySet().toString());

        var m2 = asMap(ProblemRefParser.parse("洛谷P1001", null));
        assertTrue(m2.containsKey("LUOGU:P1001"), m2.keySet().toString());
    }

    @Test
    @DisplayName("CF 斜杠写法 1932/D")
    void codeforcesSlash() {
        var m = asMap(ProblemRefParser.parse("CODEFORCES 1932/D", null));
        assertTrue(m.containsKey("CODEFORCES:1932D"), m.keySet().toString());
    }

    @Test
    @DisplayName("整行 URL")
    void urls() {
        var m = asMap(ProblemRefParser.parse("""
                https://codeforces.com/problemset/problem/1932/D
                https://www.luogu.com.cn/problem/P1001
                https://atcoder.jp/contests/abc300/tasks/abc300_a
                https://ac.nowcoder.com/acm/problem/12345
                https://leetcode.cn/problems/two-sum/
                """, null));
        assertTrue(m.containsKey("CODEFORCES:1932D"), m.keySet().toString());
        assertTrue(m.containsKey("LUOGU:P1001"));
        assertTrue(m.containsKey("ATCODER:abc300_a"));
        assertTrue(m.containsKey("NOWCODER:12345"));
        assertTrue(m.containsKey("LEETCODE:two-sum"));
    }

    @Test
    @DisplayName("CF contest 形式的 URL 也认")
    void codeforcesContestUrl() {
        var m = asMap(ProblemRefParser.parse(
                "https://codeforces.com/contest/1932/problem/A", null));
        assertTrue(m.containsKey("CODEFORCES:1932A"), m.keySet().toString());
    }

    @Test
    @DisplayName("多行混排 + 每行独立平台上下文")
    void mixedLines() {
        var r = ProblemRefParser.parse("""
                CF 1932A 1932B
                洛谷 P1001
                https://atcoder.jp/contests/abc300/tasks/abc300_a
                """, null);
        assertEquals(4, r.problems().size(), r.problems().toString());
        assertTrue(r.unparsed().isEmpty(), r.unparsed().toString());
    }

    @Test
    @DisplayName("默认平台：行内没写平台时用它")
    void defaultPlatform() {
        var m = asMap(ProblemRefParser.parse("P1001\nP1046", "LUOGU"));
        assertTrue(m.containsKey("LUOGU:P1001"), m.keySet().toString());
        assertTrue(m.containsKey("LUOGU:P1046"));

        // 不传默认平台且行内没有平台 → 无法识别
        var r = ProblemRefParser.parse("P1001", null);
        assertTrue(r.problems().isEmpty());
        assertEquals(1, r.unparsed().size());
    }

    @Test
    @DisplayName("默认平台可被行内平台覆盖")
    void linePlatformOverridesDefault() {
        var m = asMap(ProblemRefParser.parse("P1001\nCF 1932A", "LUOGU"));
        assertTrue(m.containsKey("LUOGU:P1001"), m.keySet().toString());
        assertTrue(m.containsKey("CODEFORCES:1932A"), m.keySet().toString());
    }

    @Test
    @DisplayName("去重：同一题重复出现只保留一条，并计数")
    void dedupe() {
        var r = ProblemRefParser.parse("CF 1932A 1932A\n1932A", "CODEFORCES");
        assertEquals(1, r.problems().size());
        assertEquals(2, r.duplicateCount());
    }

    @Test
    @DisplayName("无法识别的行原样回报，不静默丢弃")
    void unparsedReported() {
        var r = ProblemRefParser.parse("这是一句无关的话\nhttps://example.com/whatever", null);
        assertTrue(r.problems().isEmpty());
        assertEquals(2, r.unparsed().size(), r.unparsed().toString());
    }

    @Test
    @DisplayName("括号与引号会被剥掉")
    void trimsPunctuation() {
        var m = asMap(ProblemRefParser.parse("洛谷 (P1001) 「P1046」", null));
        assertTrue(m.containsKey("LUOGU:P1001"), m.keySet().toString());
        assertTrue(m.containsKey("LUOGU:P1046"), m.keySet().toString());
    }

    @Test
    @DisplayName("AtCoder 无下划线写法也能认")
    void atcoderNoUnderscore() {
        var m = asMap(ProblemRefParser.parse("ATCODER abc300a", null));
        assertTrue(m.containsKey("ATCODER:abc300_a"), m.keySet().toString());
    }

    @Test
    @DisplayName("空输入不炸")
    void emptyInput() {
        assertTrue(ProblemRefParser.parse(null, null).isEmpty());
        assertTrue(ProblemRefParser.parse("   \n\n  ", null).isEmpty());
    }

    @Test
    @DisplayName("生成的链接可用（抽查各平台）")
    void generatedUrls() {
        var m = asMap(ProblemRefParser.parse("""
                CF 1932A
                洛谷 P1001
                ATCODER abc300_a
                牛客 12345
                LC two-sum
                """, null));
        assertEquals("https://codeforces.com/problemset/problem/1932/A", m.get("CODEFORCES:1932A"));
        assertEquals("https://www.luogu.com.cn/problem/P1001", m.get("LUOGU:P1001"));
        assertEquals("https://atcoder.jp/contests/abc300/tasks/abc300_a", m.get("ATCODER:abc300_a"));
        assertEquals("https://ac.nowcoder.com/acm/problem/12345", m.get("NOWCODER:12345"));
        assertEquals("https://leetcode.cn/problems/two-sum/", m.get("LEETCODE:two-sum"));
    }

    @Test
    @DisplayName("URL 解析失败返回 null 而不是抛异常")
    void urlEdgeCases() {
        assertNull(ProblemRefParser.parseUrl("https://codeforces.com/problemset"));
        assertNull(ProblemRefParser.parseUrl("not a url"));
        assertNotNull(ProblemRefParser.parseUrl("https://www.luogu.com.cn/problem/P1001"));
    }

    @Test
    @DisplayName("所有平台别名都能规范化（中文别名也要能识别）")
    void aliasesCoverAllPlatforms() {
        // 别名 → 期望平台。中文别名以前只走「行内平台名」分支，
        // 紧贴题号的写法（洛谷P1001）识别不了，这里一并覆盖。
        // Map.of 最多 10 组，这里 12 组所以要 ofEntries
        var expectations = Map.ofEntries(
                Map.entry("cf 1932A", "CODEFORCES:1932A"),
                Map.entry("codeforces 1932A", "CODEFORCES:1932A"),
                Map.entry("at abc300_a", "ATCODER:abc300_a"),
                Map.entry("atcoder abc300_a", "ATCODER:abc300_a"),
                Map.entry("lg P1001", "LUOGU:P1001"),
                Map.entry("luogu P1001", "LUOGU:P1001"),
                Map.entry("洛谷 P1001", "LUOGU:P1001"),
                Map.entry("nc 12345", "NOWCODER:12345"),
                Map.entry("nowcoder 12345", "NOWCODER:12345"),
                Map.entry("牛客 12345", "NOWCODER:12345"),
                Map.entry("lc two-sum", "LEETCODE:two-sum"),
                Map.entry("力扣 two-sum", "LEETCODE:two-sum"));

        for (var e : expectations.entrySet()) {
            var keys = asMap(ProblemRefParser.parse(e.getKey(), null)).keySet();
            assertTrue(keys.contains(e.getValue()),
                    "输入 [" + e.getKey() + "] 应解析出 " + e.getValue() + "，实际 " + keys);
        }
    }

    @Test
    @DisplayName("中文别名紧贴题号：洛谷P1001 / 牛客12345")
    void chineseInlinePrefix() {
        var m = asMap(ProblemRefParser.parse("洛谷P1001", null));
        assertTrue(m.containsKey("LUOGU:P1001"), m.keySet().toString());

        var m2 = asMap(ProblemRefParser.parse("牛客12345", null));
        assertTrue(m2.containsKey("NOWCODER:12345"), m2.keySet().toString());
    }
}
