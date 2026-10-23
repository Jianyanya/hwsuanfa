package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.DifficultyScore;
import com.hwsuanfa.algo.mapper.DifficultyScoreMapper;
import com.hwsuanfa.algo.service.DifficultyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifficultyServiceImpl implements DifficultyService {

    private final DifficultyScoreMapper mapper;

    @Override
    public List<DifficultyScore> tiersOf(String platform) {
        if (platform == null || platform.isBlank()) {
            return Collections.emptyList();
        }
        return mapper.selectList(new LambdaQueryWrapper<DifficultyScore>()
                .eq(DifficultyScore::getPlatform, platform.trim().toUpperCase())
                .orderByAsc(DifficultyScore::getSortOrder)
                .orderByAsc(DifficultyScore::getId));
    }

    @Override
    public Map<String, List<DifficultyScore>> allTiers() {
        List<DifficultyScore> all = mapper.selectList(new LambdaQueryWrapper<DifficultyScore>()
                .orderByAsc(DifficultyScore::getPlatform)
                .orderByAsc(DifficultyScore::getSortOrder));
        Map<String, List<DifficultyScore>> grouped = new LinkedHashMap<>();
        for (DifficultyScore d : all) {
            grouped.computeIfAbsent(d.getPlatform(), k -> new ArrayList<>()).add(d);
        }
        return grouped;
    }

    @Override
    public BigDecimal resolveScore(String platform, String difficulty, BigDecimal fallback) {
        String p = platform == null ? "" : platform.trim().toUpperCase();
        String key = difficulty == null ? "" : difficulty.trim();

        // ① 精确匹配平台 + 档位
        if (!key.isEmpty() && !p.isEmpty()) {
            DifficultyScore exact = mapper.selectOne(new LambdaQueryWrapper<DifficultyScore>()
                    .eq(DifficultyScore::getPlatform, p)
                    .eq(DifficultyScore::getDifficultyKey, key));
            if (exact != null) {
                return exact.getScore();
            }
            log.debug("难度档位未配置，回落兜底：platform={} difficulty={}", p, key);
        }

        // ② 该平台的兜底档（题目没标难度、或标了表里没有的档位）
        if (!p.isEmpty()) {
            DifficultyScore def = mapper.selectOne(new LambdaQueryWrapper<DifficultyScore>()
                    .eq(DifficultyScore::getPlatform, p)
                    .eq(DifficultyScore::getIsDefault, true)
                    .last("LIMIT 1"));
            if (def != null) {
                return def.getScore();
            }
        }

        // ③ 最后用 score_rule 里 FIRST_AC 配的兜底分
        return fallback == null ? BigDecimal.ZERO : fallback;
    }

    @Override
    @Transactional
    public void replaceTiers(String platform, List<DifficultyScore> tiers, Long operatorId) {
        String p = platform == null ? "" : platform.trim().toUpperCase();
        if (p.isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "缺少平台标识");
        }

        // 整组替换：先清空该平台，再按传入顺序写入。
        // 这样前端可以「拖拽排序 + 一次保存」，不用担心增量 diff 的边界情况。
        mapper.delete(new LambdaQueryWrapper<DifficultyScore>().eq(DifficultyScore::getPlatform, p));

        int order = 0;
        boolean hasDefault = false;
        for (DifficultyScore t : tiers) {
            DifficultyScore e = new DifficultyScore();
            e.setPlatform(p);
            e.setDifficultyKey(t.getDifficultyKey() == null ? "" : t.getDifficultyKey().trim());
            e.setLabel(t.getLabel() == null ? e.getDifficultyKey() : t.getLabel().trim());
            e.setScore(t.getScore() == null ? BigDecimal.ZERO : t.getScore());
            e.setSortOrder(order++);
            e.setIsDefault(Boolean.TRUE.equals(t.getIsDefault()));
            if (Boolean.TRUE.equals(e.getIsDefault())) {
                hasDefault = true;
            }
            try {
                mapper.insert(e);
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                throw BizException.of(ErrorCode.CONFLICT,
                        "档位标识重复：" + e.getDifficultyKey());
            }
        }

        if (!tiers.isEmpty() && !hasDefault) {
            // 没有兜底档的话，题目标了表外的难度时会一路回落到 score_rule，
            // 分值可能偏离预期，所以提醒一下（不阻断保存）
            log.warn("平台 {} 的难度分档没有设置兜底档，未匹配的难度将回落到 FIRST_AC 的默认分值", p);
        }
    }
}
