package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.dto.ProblemRequest;
import com.hwsuanfa.algo.entity.AssignmentProblem;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.mapper.AssignmentProblemMapper;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.DifficultyService;
import com.hwsuanfa.algo.service.ProblemService;
import com.hwsuanfa.algo.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProblemServiceImpl implements ProblemService {

    private static final int MAX_LIMIT = 100;

    /** 题目难度缺失时的兜底分值，与 AssignmentServiceImpl 保持一致 */
    private static final java.math.BigDecimal FALLBACK_FIRST_AC = new java.math.BigDecimal("2");

    private final ProblemMapper problemMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final DifficultyService difficultyService;

    @Override
    public List<ProblemVO> search(String keyword, String platform, int limit) {
        LambdaQueryWrapper<Problem> q = new LambdaQueryWrapper<Problem>()
                .orderByDesc(Problem::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), MAX_LIMIT));

        if (platform != null && !platform.isBlank()) {
            q.eq(Problem::getPlatform, platform);
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like(Problem::getTitle, k)
                    .or().like(Problem::getProblemNo, k)
                    .or().like(Problem::getTags, k));
        }

        return problemMapper.selectList(q).stream().map(p -> toVO(p, null)).toList();
    }

    @Override
    public ProblemVO detail(Long id) {
        return toVO(requireProblem(id), null);
    }

    @Override
    @Transactional
    public ProblemVO create(ProblemRequest req, LoginUser operator) {
        Problem p = new Problem();
        apply(p, req);
        p.setCreatedBy(operator.getId());
        try {
            problemMapper.insert(p);
        } catch (DuplicateKeyException e) {
            // (platform, problem_no) 唯一：同一平台的同一题号不允许重复录入
            throw BizException.of(ErrorCode.PROBLEM_DUPLICATE);
        }
        return toVO(p, null);
    }

    @Override
    @Transactional
    public ProblemVO update(Long id, ProblemRequest req, LoginUser operator) {
        Problem p = requireProblem(id);
        apply(p, req);
        try {
            problemMapper.updateById(p);
        } catch (DuplicateKeyException e) {
            throw BizException.of(ErrorCode.PROBLEM_DUPLICATE);
        }
        return toVO(p, null);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireProblem(id);
        long used = assignmentProblemMapper.selectCount(
                new LambdaQueryWrapper<AssignmentProblem>().eq(AssignmentProblem::getProblemId, id));
        if (used > 0) {
            // 直接删会让历史作业出现空题，要求先解除引用
            throw BizException.of(ErrorCode.CONFLICT,
                    "该题目已被 " + used + " 个作业引用，请先从作业中移除");
        }
        problemMapper.deleteById(id);
    }

    @Override
    public Map<Long, Problem> loadByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return problemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
    }

    @Override
    @Transactional
    public Problem findOrCreate(String platform, String problemNo, String url, Long operatorId) {
        String p = platform == null ? "OTHER" : platform.trim().toUpperCase();
        String no = problemNo == null ? "" : problemNo.trim();

        Problem existing = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPlatform, p)
                .eq(Problem::getProblemNo, no));
        if (existing != null) {
            // 已有记录：如果原来没链接而这次解析出了链接，顺手补上
            if (url != null && !url.isBlank()
                    && (existing.getUrl() == null || existing.getUrl().isBlank())) {
                Problem patch = new Problem();
                patch.setId(existing.getId());
                patch.setUrl(url);
                problemMapper.updateById(patch);
                existing.setUrl(url);
            }
            return existing;
        }

        Problem created = new Problem();
        // 我们不抓题面，标题先用「平台 题号」占位，站主之后可人工补充
        created.setTitle(no.isEmpty() ? p : p + " " + no);
        created.setPlatform(p);
        created.setProblemNo(no);
        created.setUrl(url);
        created.setCreatedBy(operatorId);
        try {
            problemMapper.insert(created);
        } catch (DuplicateKeyException e) {
            // 并发导入同一个题号时由唯一索引兜底，重查一次即可
            return problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                    .eq(Problem::getPlatform, p)
                    .eq(Problem::getProblemNo, no));
        }
        return created;
    }

    private Problem requireProblem(Long id) {
        Problem p = problemMapper.selectById(id);
        if (p == null) {
            throw BizException.of(ErrorCode.PROBLEM_NOT_FOUND);
        }
        return p;
    }

    private void apply(Problem p, ProblemRequest req) {
        p.setTitle(req.getTitle().trim());
        p.setPlatform(req.getPlatform().trim().toUpperCase());
        // 没有题号的题目（如自编题）用空串占位，避免唯一索引里 NULL 不参与去重的问题
        String no = req.getProblemNo() == null ? "" : req.getProblemNo().trim();
        p.setProblemNo(no);
        p.setUrl(req.getUrl());
        p.setDifficulty(req.getDifficulty());
        p.setTags(req.getTags());
        p.setNote(req.getNote());
    }

    private ProblemVO toVO(Problem p, Boolean done) {
        return ProblemVO.builder()
                .id(p.getId())
                .title(p.getTitle())
                .platform(p.getPlatform())
                .problemNo(p.getProblemNo())
                .url(p.getUrl())
                .difficulty(p.getDifficulty())
                .tags(p.getTags())
                .done(done)
                .score(difficultyService.resolveScore(p.getPlatform(), p.getDifficulty(), FALLBACK_FIRST_AC))
                .build();
    }
}
