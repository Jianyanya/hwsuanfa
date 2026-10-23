package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.entity.DifficultyScore;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 难度分档。
 *
 * 分值属于教学策略，按**平台 + 档位**配置，站主可随时调整，不需要改代码。
 * 查不到时按「精确匹配 → 平台兜底档 → 全局兜底分」三级回落，
 * 保证任何情况都不会出现「做完了却拿不到分」。
 */
public interface DifficultyService {

    /** 某平台的档位（按由易到难排序），用于前端下拉与后台配置 */
    List<DifficultyScore> tiersOf(String platform);

    /** 所有平台的档位，按平台分组 */
    Map<String, List<DifficultyScore>> allTiers();

    /**
     * 取某道题的分值。
     *
     * @param platform    题目平台
     * @param difficulty  题目标注的难度档位，可为空
     * @param fallback    最后兜底分（一般为 score_rule 里 FIRST_AC 的配置）
     */
    BigDecimal resolveScore(String platform, String difficulty, BigDecimal fallback);

    /** 批量替换某平台的档位配置（后台保存用） */
    void replaceTiers(String platform, List<DifficultyScore> tiers, Long operatorId);
}
