package com.hwsuanfa.algo.common;

import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.entity.SubmissionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubmissionInsightsTest {

    @ParameterizedTest
    @CsvSource({
            "CODEFORCES,800-1299,简单",
            "CODEFORCES,1299,简单",
            "CODEFORCES,1300-1399,简单",
            "CODEFORCES,1399,简单",
            "CODEFORCES,1400-1499,中等",
            "CODEFORCES,1400,中等",
            "CODEFORCES,1999,中等",
            "CODEFORCES,2000-2299,困难",
            "CODEFORCES,2600+,困难",
            "LUOGU,入门,简单",
            "LUOGU,普及−,中等",
            "LUOGU,普及,中等",
            "LUOGU,普及/提高−,中等",
            "LUOGU,普及+/提高,中等",
            "LUOGU,普及+/提高−,中等",
            "LUOGU,提高,困难",
            "LUOGU,提高−,困难",
            "LUOGU,提高+/省选−,困难",
            "LUOGU,省选/NOI−,困难",
            "LUOGU,NOI/NOI+/CTSC,困难",
            "LUOGU,2,中等",
            "LUOGU,3,中等",
            "LUOGU,5,困难",
            "ATCODER,ABC-A/B,简单",
            "ATCODER,ABC-C,简单",
            "ATCODER,ABC-D,中等",
            "ATCODER,ABC-E,中等",
            "ATCODER,ABC-F,困难",
            "ATCODER,ABC-G/ARC/AGC,困难",
            "ATCODER,0,简单",
            "ATCODER,80,简单",
            "ATCODER,-100,简单",
            "ATCODER,0-399,简单",
            "ATCODER,399,简单",
            "ATCODER,400,简单",
            "ATCODER,1199,简单",
            "ATCODER,1200,简单",
            "ATCODER,1399,简单",
            "ATCODER,1400,中等",
            "ATCODER,1999,中等",
            "ATCODER,2000,困难",
            "NOWCODER,简单,简单",
            "NOWCODER,中等,中等",
            "NOWCODER,较难,困难",
            "LEETCODE,Easy,简单",
            "LEETCODE,Medium,中等",
            "LEETCODE,Hard,困难"
    })
    void groupsPlatformDifficulties(String platform, String difficulty, String expected) {
        assertEquals(expected, SubmissionInsights.difficultyOf(platform, difficulty));
    }

    @Test
    void unknownDifficultyIsNotCountedAsEasy() {
        assertNull(SubmissionInsights.difficultyOf("LUOGU", null));
        assertNull(SubmissionInsights.difficultyOf("LUOGU", " "));
        assertNull(SubmissionInsights.difficultyOf("LUOGU", "暂无评定"));
        assertNull(SubmissionInsights.difficultyOf("LUOGU", "0"));
        assertNull(SubmissionInsights.difficultyOf("OTHER", "未评级"));
    }

    @Test
    void countsSolvedProblemsAndDeduplicatesAbilityAliases() {
        var records = List.of(
                submission(1L, "LUOGU", "AC"), submission(1L, "LUOGU", "AC"),
                submission(2L, "CODEFORCES", "AC"), submission(3L, "LUOGU", "AC"),
                submission(4L, "ATCODER", "AC"), submission(5L, "LUOGU", "WA"));
        var problems = Map.of(
                1L, problem("普及/提高−", "dp,DP,动态规划,greedy,贪心"),
                2L, problem("1600-1699", "枚举,brute force,implementation"),
                3L, problem(null, "搜索,DFS,图论"),
                5L, problem("入门", "数学"));

        Map<String, Object> data = SubmissionInsights.summarize(records, problems);

        assertEquals(4, data.get("totalAc"));
        assertEquals(3, data.get("abilityTaggedAc"));
        assertEquals(2, data.get("unknownDifficulty"));
        assertEquals(Map.of("LUOGU", 2, "CODEFORCES", 1, "ATCODER", 1), counts(data, "byPlatform"));
        assertEquals(Map.of("简单", 0, "中等", 2, "困难", 0), counts(data, "byDifficulty"));
        assertEquals(Map.of("贪心", 1, "模拟", 1, "枚举", 1, "DP", 1,
                "搜索", 1, "图论", 1, "数据结构", 0, "数学", 0), counts(data, "byAbility"));
        assertEquals(1, counts(data, "byTag").get("动态规划"));
        assertEquals(1, counts(data, "byTag").get("枚举"));
    }

    @Test
    void emptyDataRetainsAllAbilityAndDifficultyAxes() {
        Map<String, Object> data = SubmissionInsights.summarize(List.of(), Map.of());
        assertEquals(0, data.get("totalAc"));
        assertEquals(8, counts(data, "byAbility").size());
        assertEquals(Map.of("简单", 0, "中等", 0, "困难", 0), counts(data, "byDifficulty"));
    }

    private SubmissionRecord submission(Long problemId, String platform, String result) {
        SubmissionRecord record = new SubmissionRecord();
        record.setProblemId(problemId);
        record.setPlatform(platform);
        record.setResult(result);
        return record;
    }

    private Problem problem(String difficulty, String tags) {
        Problem problem = new Problem();
        problem.setDifficulty(difficulty);
        problem.setTags(tags);
        return problem;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> counts(Map<String, Object> data, String key) {
        return ((List<Map<String, Object>>) data.get(key)).stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> (String) item.get("name"), item -> (Integer) item.get("count")));
    }
}
