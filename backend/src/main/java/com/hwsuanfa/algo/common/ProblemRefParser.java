package com.hwsuanfa.algo.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 题号批量粘贴解析器。
 *
 * 定位：我们**不是 OJ**，不从平台上抓题面、不判题。这里只做一件事 ——
 * 把站主/队长粘进来的一坨文本，解析成「平台 + 题号」这种结构化引用。
 * 标题和难度都可以留空，页面直接显示题号加外链就够了。
 *
 * 支持的写法（大小写不敏感）：
 * <pre>
 *   CF 1932A 1932B 1932C        // 平台前缀 + 多个题号
 *   洛谷 P1001, P1046            // 中文平台名 + 中文逗号
 *   CODEFORCES 1932/D            // 斜杠写法
 *   CF1932A                      // 前缀紧贴题号
 *   https://codeforces.com/problemset/problem/1932/D
 *   https://www.luogu.com.cn/problem/P1001
 *   https://atcoder.jp/contests/abc300/tasks/abc300_a
 *   https://leetcode.cn/problems/two-sum/
 *   https://ac.nowcoder.com/acm/problem/12345
 * </pre>
 *
 * 无法识别的行会原样回报，不静默丢弃 —— 否则用户不知道哪几行没进去。
 * 纯函数、无 Spring 依赖，便于单测。
 */
public final class ProblemRefParser {

    /** 解析出的一条题目引用 */
    public record ParsedProblem(String platform, String problemNo, String url) {
    }

    /** 解析结果 */
    public record Result(List<ParsedProblem> problems, List<String> unparsed,
                         int duplicateCount, int totalTokens) {
        public boolean isEmpty() {
            return problems.isEmpty();
        }
    }

    /* ---------- 平台别名 ---------- */
    private static final Map<String, String> ALIASES = buildAliases();

    private static Map<String, String> buildAliases() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String a : List.of("cf", "codeforces", "codeforces.com")) {
            m.put(a, "CODEFORCES");
        }
        for (String a : List.of("at", "atcoder", "atcoder.jp", "ac")) {
            m.put(a, "ATCODER");
        }
        for (String a : List.of("lg", "luogu", "洛谷", "luogu.com.cn")) {
            m.put(a, "LUOGU");
        }
        for (String a : List.of("nc", "nowcoder", "牛客", "nowcoder.com")) {
            m.put(a, "NOWCODER");
        }
        for (String a : List.of("lc", "leetcode", "力扣", "leetcode.cn", "leetcode.com")) {
            m.put(a, "LEETCODE");
        }
        return m;
    }

    /* ---------- URL 规则 ---------- */
    private static final List<UrlRule> URL_RULES = List.of(
            new UrlRule("codeforces\\.com|codeforces\\.ml",
                    Pattern.compile("/(?:problemset/problem|contest)/(\\d+)/(?:problem/)?([A-Za-z]\\d?)")),
            new UrlRule("luogu\\.com\\.cn|\\.luogu\\.com",
                    Pattern.compile("/problem/([A-Za-z]{1,3}\\d{1,7})")),
            new UrlRule("atcoder\\.jp",
                    Pattern.compile("/tasks/([A-Za-z0-9_-]+)")),
            new UrlRule("nowcoder\\.com",
                    Pattern.compile("/(?:acm/problem|problem)/(\\d{3,8})")),
            new UrlRule("leetcode\\.(?:cn|com)",
                    Pattern.compile("/problems/([A-Za-z0-9-]+)"))
    );

    private record UrlRule(String hostPattern, Pattern pathPattern) {
    }

    /* ---------- 各平台「裸题号」的识别规则 ---------- */
    private static final Pattern CF_ID = Pattern.compile("^(\\d{2,4})/([A-Za-z]\\d?)$|^(\\d{2,4})([A-Za-z]\\d?)$");
    private static final Pattern LUOGU_ID = Pattern.compile("^([A-Za-z]{1,3}\\d{1,7})$");
    private static final Pattern ATCODER_ID = Pattern.compile("^([A-Za-z]{2,5}\\d{2,4})[_-]?([A-Za-z]\\d?)$");
    private static final Pattern NOWCODER_ID = Pattern.compile("^(\\d{3,8})$");
    /** LeetCode 裸题号只接受带连字符的 slug（two-sum），否则太容易误判普通单词 */
    private static final Pattern LEETCODE_ID = Pattern.compile("^([a-z0-9]+(?:-[a-z0-9]+)+)$");

    /** 分隔符：空白 + 中英文逗号/分号/顿号 */
    private static final Pattern SEPARATOR = Pattern.compile("[\\s,，;；、]+");

    private ProblemRefParser() {
    }

    /**
     * 解析。
     *
     * @param text            粘贴的原文
     * @param defaultPlatform 行内没写平台时用的默认平台，可为 null（则视为无法识别）
     */
    public static Result parse(String text, String defaultPlatform) {
        List<ParsedProblem> problems = new ArrayList<>();
        List<String> unparsed = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int duplicates = 0;
        int tokens = 0;

        if (text == null || text.isBlank()) {
            return new Result(problems, unparsed, 0, 0);
        }

        String fallback = normalizePlatform(defaultPlatform);

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            // ① 整行是 URL
            ParsedProblem byUrl = parseUrl(line);
            if (byUrl != null) {
                tokens++;
                if (seen.add(byUrl.platform() + "|" + byUrl.problemNo())) {
                    problems.add(byUrl);
                } else {
                    duplicates++;
                }
                continue;
            }

            // ② 逐 token 解析
            String[] parts = SEPARATOR.split(line);
            String current = fallback;
            boolean consumedAny = false;

            for (String part : parts) {
                String token = trimPunctuation(part);
                if (token.isEmpty()) {
                    continue;
                }

                // token 本身是平台名 → 切换本行的当前平台
                String asPlatform = normalizePlatform(token);
                if (asPlatform != null) {
                    current = asPlatform;
                    continue;
                }

                // token 是 URL（URL 不一定独占一行，可能夹在说明文字里）
                ParsedProblem asUrl = parseUrl(token);
                if (asUrl != null) {
                    tokens++;
                    consumedAny = true;
                    if (seen.add(asUrl.platform() + "|" + asUrl.problemNo())) {
                        problems.add(asUrl);
                    } else {
                        duplicates++;
                    }
                    continue;
                }

                // token 是「紧贴前缀 + 题号」，如 CF1932A
                ParsedProblem inline = parseInlinePrefixed(token);
                if (inline != null) {
                    tokens++;
                    consumedAny = true;
                    if (seen.add(inline.platform() + "|" + inline.problemNo())) {
                        problems.add(inline);
                    } else {
                        duplicates++;
                    }
                    continue;
                }

                // 按当前平台识别裸题号
                if (current != null) {
                    String no = normalizeProblemNo(current, token);
                    if (no != null) {
                        tokens++;
                        consumedAny = true;
                        if (seen.add(current + "|" + no)) {
                            problems.add(new ParsedProblem(current, no, buildUrl(current, no)));
                        } else {
                            duplicates++;
                        }
                        continue;
                    }
                }

                // 认不出来：继续试后面的 token，不要提前 break。
                // 之前这里一遇到不认识的 token 就 break，导致
                // 「URL https://codeforces.com/.../1932/D 说明文字」这种行里
                // 排在后面的 URL 根本没机会被解析。
                tokens++;
            }

            if (!consumedAny) {
                unparsed.add(line);
            }
        }

        return new Result(problems, unparsed, duplicates, tokens);
    }

    /** 解析整行 URL */
    public static ParsedProblem parseUrl(String text) {
        String s = text.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            return null;
        }
        String lower = s.toLowerCase();
        for (UrlRule rule : URL_RULES) {
            if (!Pattern.compile(rule.hostPattern()).matcher(lower).find()) {
                continue;
            }
            Matcher m = rule.pathPattern().matcher(s);
            if (!m.find()) {
                continue;
            }
            String platform = platformOfHost(rule.hostPattern());
            if (platform == null) {
                continue;
            }
            String no = formatFromUrl(platform, m);
            if (no == null) {
                return null;
            }
            return new ParsedProblem(platform, no, s);
        }
        return null;
    }

    private static String platformOfHost(String hostPattern) {
        if (hostPattern.contains("codeforces")) return "CODEFORCES";
        if (hostPattern.contains("luogu")) return "LUOGU";
        if (hostPattern.contains("atcoder")) return "ATCODER";
        if (hostPattern.contains("nowcoder")) return "NOWCODER";
        if (hostPattern.contains("leetcode")) return "LEETCODE";
        return null;
    }

    private static String formatFromUrl(String platform, Matcher m) {
        return switch (platform) {
            // /problemset/problem/1932/D 或 /contest/1932/problem/D → 统一成 1932D
            case "CODEFORCES" -> m.group(1) + m.group(2).toUpperCase();
            case "LUOGU" -> m.group(1).toUpperCase();
            case "ATCODER" -> m.group(1).toLowerCase();
            case "NOWCODER" -> m.group(1);
            case "LEETCODE" -> m.group(1).toLowerCase();
            default -> null;
        };
    }

    /** 处理 CF1932A / 洛谷P1001 / luoguP1001 这种前缀紧贴题号的写法 */
    private static ParsedProblem parseInlinePrefixed(String token) {
        String lower = token.toLowerCase();
        // 按别名长度从长到短试：否则 "LG" 会被更短的别名抢先匹配，
        // 而且必须走**全部别名**（含中文），不能只比拉丁前缀 —— 中文别名没有大小写，
        // 之前只匹配大写拉丁前缀，导致「洛谷P1001」这种写法直接被判为无法识别。
        for (String alias : ALIASES_BY_LENGTH_DESC) {
            if (!lower.startsWith(alias) || lower.length() == alias.length()) {
                continue;
            }
            String platform = ALIASES.get(alias);
            String rest = token.substring(alias.length());
            String no = normalizeProblemNo(platform, rest);
            if (no != null) {
                return new ParsedProblem(platform, no, buildUrl(platform, no));
            }
        }
        return null;
    }

    /** 别名按长度倒序，保证长别名优先匹配 */
    private static final List<String> ALIASES_BY_LENGTH_DESC =
            ALIASES.keySet().stream()
                    .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                    .toList();

    private static String normalizePlatform(String raw) {
        if (raw == null) {
            return null;
        }
        return ALIASES.get(raw.trim().toLowerCase());
    }

    /** 把某个 token 规范化成该平台的题号；不匹配返回 null */
    private static String normalizeProblemNo(String platform, String token) {
        String t = trimPunctuation(token);
        if (t.isEmpty()) {
            return null;
        }
        return switch (platform) {
            case "CODEFORCES" -> {
                Matcher m = CF_ID.matcher(t);
                if (m.matches()) {
                    yield m.group(1) != null
                            ? m.group(1) + m.group(2).toUpperCase()   // 1932/D
                            : m.group(3) + m.group(4).toUpperCase();  // 1932D
                }
                yield null;
            }
            case "LUOGU" -> {
                Matcher m = LUOGU_ID.matcher(t);
                yield m.matches() ? m.group(1).toUpperCase() : null;
            }
            case "ATCODER" -> {
                Matcher m = ATCODER_ID.matcher(t);
                yield m.matches() ? (m.group(1) + "_" + m.group(2)).toLowerCase() : null;
            }
            case "NOWCODER" -> {
                Matcher m = NOWCODER_ID.matcher(t);
                yield m.matches() ? m.group(1) : null;
            }
            case "LEETCODE" -> {
                Matcher m = LEETCODE_ID.matcher(t.toLowerCase());
                yield m.matches() ? m.group(1) : null;
            }
            default -> t;
        };
    }

    /**
     * 去掉 token 前后的括号、引号等。
     * 必须同时处理**中文标点**（「」『』【】（）《》），
     * 否则「P1046」这种带中文引号的题号会被判为不合法而丢掉。
     */
    private static String trimPunctuation(String s) {
        return s.replaceAll("^[\\[(\\{\"'`「『【（《〈]+", "")
                .replaceAll("[\\])\\}\"'`,.。、」』】》）›〉]+$", "");
    }

    private static String buildUrl(String platform, String no) {
        return switch (platform) {
            case "CODEFORCES" -> {
                Matcher m = CF_ID.matcher(no);
                if (m.matches() && m.group(1) != null) {
                    yield "https://codeforces.com/problemset/problem/" + m.group(1) + "/" + m.group(2);
                }
                // 1932A → 拆成 1932 / A
                Matcher m2 = Pattern.compile("^(\\d{2,4})([A-Za-z]\\d?)$").matcher(no);
                yield m2.matches()
                        ? "https://codeforces.com/problemset/problem/" + m2.group(1) + "/" + m2.group(2).toUpperCase()
                        : null;
            }
            case "LUOGU" -> "https://www.luogu.com.cn/problem/" + no;
            case "ATCODER" -> "https://atcoder.jp/contests/" + contestOf(no) + "/tasks/" + no;
            case "NOWCODER" -> "https://ac.nowcoder.com/acm/problem/" + no;
            case "LEETCODE" -> "https://leetcode.cn/problems/" + no + "/";
            default -> null;
        };
    }

    /** abc300_a → abc300 */
    private static String contestOf(String atcoderNo) {
        int idx = atcoderNo.indexOf('_');
        return idx > 0 ? atcoderNo.substring(0, idx) : atcoderNo.replaceAll("_?[a-z]\\d?$", "");
    }

    /** 便于日志/测试：把结果拼成一行 */
    public static String describe(Result r) {
        return "识别 " + r.problems().size() + " 题，跳过重复 " + r.duplicateCount()
                + "，未识别 " + r.unparsed().size() + " 行";
    }

    static {
        // 防御：确保别名表没有 null 值（改代码时容易手滑）
        if (ALIASES.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("平台别名表存在空值");
        }
    }
}
