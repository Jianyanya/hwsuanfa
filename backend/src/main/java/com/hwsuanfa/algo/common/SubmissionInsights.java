package com.hwsuanfa.algo.common;

import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.entity.SubmissionRecord;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class SubmissionInsights {

    private static final List<String> ABILITIES = List.of(
            "贪心", "模拟", "枚举", "DP", "搜索", "图论", "数据结构", "数学");
    private static final Map<String, List<String>> ABILITY_TAGS = Map.of(
            "贪心", List.of("贪心", "greedy"),
            "模拟", List.of("模拟", "implementation", "simulation"),
            "枚举", List.of("枚举", "暴力", "穷举", "brute force", "enumeration"),
            "DP", List.of("动态规划", "dp", "dynamic programming"),
            "搜索", List.of("搜索", "回溯", "dfs", "bfs", "深度优先", "广度优先", "二分", "双指针"),
            "图论", List.of("图论", "最短路", "生成树", "网络流", "二分图", "拓扑", "连通", "graphs", "shortest paths"),
            "数据结构", List.of("数据结构", "树", "堆", "栈", "队列", "并查集", "哈希", "链表", "data structures", "trees", "hashing"),
            "数学", List.of("数学", "数论", "组合", "概率", "期望", "几何", "博弈", "math", "number theory", "combinatorics", "geometry", "probabilities", "games"));
    private static final Map<String, String> TAG_ALIASES = Map.ofEntries(
            Map.entry("greedy", "贪心"), Map.entry("implementation", "模拟"),
            Map.entry("simulation", "模拟"), Map.entry("brute force", "枚举"),
            Map.entry("enumeration", "枚举"), Map.entry("暴力", "枚举"),
            Map.entry("穷举", "枚举"), Map.entry("dp", "动态规划"),
            Map.entry("dynamic programming", "动态规划"), Map.entry("graphs", "图论"),
            Map.entry("data structures", "数据结构"), Map.entry("trees", "树"),
            Map.entry("math", "数学"), Map.entry("number theory", "数论"),
            Map.entry("combinatorics", "组合数学"), Map.entry("geometry", "几何"),
            Map.entry("binary search", "二分"), Map.entry("dfs and similar", "DFS"),
            Map.entry("two pointers", "双指针"), Map.entry("hashing", "哈希"),
            Map.entry("shortest paths", "最短路"), Map.entry("probabilities", "概率"),
            Map.entry("games", "博弈"));
    private static final Pattern RATING = Pattern.compile("(?<!\\d)(\\d{3,4})(?!\\d)");
    private static final Pattern ATCODER_RATING = Pattern.compile("(?<!\\d)(-?\\d{1,4})(?!\\d)");

    private SubmissionInsights() {
    }

    public static Map<String, Object> summarize(List<SubmissionRecord> submissions, Map<Long, Problem> problems) {
        Map<String, SubmissionRecord> solved = new LinkedHashMap<>();
        for (SubmissionRecord record : submissions) {
            if (!"AC".equals(record.getResult())) continue;
            String key = record.getProblemId() == null
                    ? record.getPlatform() + ":" + record.getProblemNo()
                    : "id:" + record.getProblemId();
            solved.putIfAbsent(key, record);
        }

        Map<String, Integer> byPlatform = new TreeMap<>();
        Map<String, Integer> byDifficulty = new LinkedHashMap<>();
        for (String name : List.of("简单", "中等", "困难")) byDifficulty.put(name, 0);
        Map<String, Integer> byAbility = new LinkedHashMap<>();
        for (String name : ABILITIES) byAbility.put(name, 0);
        Map<String, Integer> byTag = new TreeMap<>();
        int unknownDifficulty = 0;
        int abilityTaggedAc = 0;

        for (SubmissionRecord record : solved.values()) {
            String platform = record.getPlatform() == null ? "OTHER" : record.getPlatform().toUpperCase(Locale.ROOT);
            byPlatform.merge(platform, 1, Integer::sum);
            Problem problem = record.getProblemId() == null ? null : problems.get(record.getProblemId());
            String difficulty = difficultyOf(platform, problem == null ? null : problem.getDifficulty());
            if (difficulty == null) unknownDifficulty++;
            else byDifficulty.merge(difficulty, 1, Integer::sum);
            if (problem == null) continue;

            Set<String> tags = new LinkedHashSet<>();
            if (problem.getTags() != null) {
                for (String raw : problem.getTags().split("[,，;；]")) {
                    String tag = raw.trim();
                    if (!tag.isEmpty()) tags.add(TAG_ALIASES.getOrDefault(tag.toLowerCase(Locale.ROOT), tag));
                }
            }
            for (String tag : tags) byTag.merge(tag, 1, Integer::sum);
            boolean hasAbility = false;
            for (String ability : ABILITIES) {
                boolean matched = tags.stream().map(t -> t.toLowerCase(Locale.ROOT))
                        .anyMatch(t -> ABILITY_TAGS.get(ability).stream().anyMatch(t::contains));
                if (matched) {
                    byAbility.merge(ability, 1, Integer::sum);
                    hasAbility = true;
                }
            }
            if (hasAbility) abilityTaggedAc++;
        }

        return Map.of(
                "totalAc", solved.size(),
                "byPlatform", counts(byPlatform),
                "byDifficulty", counts(byDifficulty),
                "unknownDifficulty", unknownDifficulty,
                "byAbility", counts(byAbility),
                "abilityTaggedAc", abilityTaggedAc,
                "byTag", byTag.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue())).toList());
    }

    public static String difficultyOf(String platform, String difficulty) {
        if (difficulty == null || difficulty.isBlank()) return null;
        String d = difficulty.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "")
                .replace('−', '-').replace('–', '-');
        if (List.of("简单", "容易", "入门", "easy", "beginner").contains(d)) return "简单";
        if (List.of("中等", "中级", "medium", "normal").contains(d)) return "中等";
        if (List.of("困难", "较难", "高难", "hard", "expert").contains(d)) return "困难";
        String p = platform == null ? "OTHER" : platform.toUpperCase(Locale.ROOT);
        if ("LUOGU".equals(p)) {
            if ("1".equals(d)) return "简单";
            if (d.startsWith("普及") || List.of("2", "3", "4").contains(d)) return "中等";
            if (d.startsWith("提高") || d.contains("省选") || d.contains("noi") || d.contains("ctsc")
                    || List.of("5", "6", "7").contains(d)) return "困难";
            return null;
        }
        if ("ATCODER".equals(p) && d.startsWith("abc-")) {
            String tasks = d.substring(4);
            if (tasks.contains("arc") || tasks.contains("agc")) return "困难";
            int hardest = Arrays.stream(tasks.split("[/,-]"))
                    .filter(s -> s.matches("[a-g]"))
                    .mapToInt(s -> s.charAt(0) - 'a').max().orElse(-1);
            if (hardest < 0) return null;
            return hardest <= 2 ? "简单" : hardest <= 4 ? "中等" : "困难";
        }
        if ("ATCODER".equals(p) && (d.startsWith("arc") || d.startsWith("agc"))) return "困难";
        var rating = ("ATCODER".equals(p) ? ATCODER_RATING : RATING).matcher(d);
        if (rating.find()) {
            int value = Integer.parseInt(rating.group(1));
            if ("CODEFORCES".equals(p) || "ATCODER".equals(p)) {
                return value < 1400 ? "简单" : value < 2000 ? "中等" : "困难";
            }
        }
        return null;
    }

    private static List<Map<String, Object>> counts(Map<String, Integer> data) {
        return data.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue())).toList();
    }
}
