package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.dto.ProblemMetadataImportRequest;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemMetadataController {

    private final ProblemMapper problemMapper;

    @PostMapping("/import-metadata/{platform}")
    @Transactional
    public ApiResult<Map<String, Integer>> importMetadata(
            @PathVariable String platform,
            @Valid @RequestBody ProblemMetadataImportRequest request,
            @AuthenticationPrincipal LoginUser me) {
        String normalized = platform == null ? "" : platform.trim().toUpperCase();
        if (!"LUOGU".equals(normalized)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "元数据导入只支持 LUOGU");
        }

        int updated = 0;
        int created = 0;
        int skipped = 0;
        for (ProblemMetadataImportRequest.Item item : request.getRecords()) {
            String problemNo = item.getProblemNo().trim().toUpperCase();
            Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                    .eq(Problem::getPlatform, normalized)
                    .eq(Problem::getProblemNo, problemNo));
            if (problem == null) {
                problem = new Problem();
                problem.setPlatform(normalized);
                problem.setProblemNo(problemNo);
                problem.setTitle(hasText(item.getTitle()) ? item.getTitle().trim() : normalized + " " + problemNo);
                problem.setUrl(blankToNull(item.getUrl()));
                problem.setCreatedBy(me.getId());
                problem.setDifficulty(blankToNull(item.getDifficulty()));
                problem.setTags(blankToNull(item.getTags()));
                if (!hasText(problem.getTags()) && !hasText(problem.getDifficulty())) {
                    skipped++;
                    continue;
                }
                try {
                    problemMapper.insert(problem);
                    created++;
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                            .eq(Problem::getPlatform, normalized)
                            .eq(Problem::getProblemNo, problemNo));
                    if (problem == null) {
                        skipped++;
                        continue;
                    }
                }
            }

            Problem patch = new Problem();
            patch.setId(problem.getId());
            boolean changed = false;
            if (hasText(item.getTitle()) && (!hasText(problem.getTitle())
                    || (normalized + " " + problemNo).equals(problem.getTitle()))) {
                patch.setTitle(item.getTitle().trim());
                changed = true;
            }
            if (hasText(item.getUrl()) && (!hasText(problem.getUrl())
                    || problem.getUrl().startsWith("https://www.luogu.com.cn/record/"))) {
                patch.setUrl(item.getUrl().trim());
                changed = true;
            }
            if (hasText(item.getDifficulty()) && !hasText(problem.getDifficulty())) {
                patch.setDifficulty(item.getDifficulty().trim());
                changed = true;
            }
            if (hasText(item.getTags()) && !hasText(problem.getTags())) {
                patch.setTags(item.getTags().trim());
                changed = true;
            }
            if (changed && problem.getId() != null) {
                problemMapper.updateById(patch);
                updated++;
            }
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("updated", updated);
        result.put("created", created);
        result.put("skipped", skipped);
        return ApiResult.ok(result);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
