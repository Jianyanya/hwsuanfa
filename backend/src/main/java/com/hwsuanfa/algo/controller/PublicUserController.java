package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.Award;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.entity.ScoreEvent;
import com.hwsuanfa.algo.entity.SubmissionRecord;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.AwardMapper;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.mapper.ScoreEventMapper;
import com.hwsuanfa.algo.mapper.SubmissionRecordMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/public/users")
@RequiredArgsConstructor
public class PublicUserController {

    private final UserMapper userMapper;
    private final SubmissionRecordMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final AwardMapper awardMapper;
    private final ScoreEventMapper scoreEventMapper;

    @GetMapping
    public ApiResult<List<Map<String, Object>>> search(@RequestParam(required = false) String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.isEmpty()) return ApiResult.ok(List.of());
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, "ACTIVE")
                .and(q -> q.like(User::getNickname, value).or().like(User::getUsername, value))
                .orderByAsc(User::getNickname)
                .last("LIMIT 20"));
        return ApiResult.ok(users.stream().filter(this::isPublic).map(this::brief).toList());
    }

    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id,
                                                 @AuthenticationPrincipal LoginUser me) {
        User user = userMapper.selectById(id);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        boolean self = me != null && Objects.equals(me.getId(), id);
        if (!self && !isPublic(user)) {
            throw new BizException(ErrorCode.NOT_FOUND, "该用户未公开个人资料");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", publicUser(user));
        body.put("stats", stats(id));
        body.put("awards", awardMapper.selectList(new LambdaQueryWrapper<Award>()
                .eq(Award::getUserId, id)
                .eq(Award::getAuditStatus, "APPROVED")
                .eq(Award::getIsPublic, true)
                .orderByDesc(Award::getAwardedAt)).stream().map(this::award).toList());
        if ("PUBLIC".equals(normalizePrivacy(user)) && Boolean.TRUE.equals(user.getShowCode())) {
            body.put("submissions", submissions(id));
        } else {
            body.put("submissions", List.of());
        }
        return ApiResult.ok(body);
    }

    private boolean isPublic(User user) {
        return !"PRIVATE".equals(normalizePrivacy(user));
    }

    private String normalizePrivacy(User user) {
        String level = user.getPrivacyLevel();
        return level == null || level.isBlank() ? "PRIVATE" : level.trim().toUpperCase();
    }

    private Map<String, Object> brief(User user) {
        return Map.of("id", user.getId(), "nickname", user.getNickname(), "avatar", user.getAvatar());
    }

    private Map<String, Object> publicUser(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("privacyLevel", normalizePrivacy(user));
        if ("PUBLIC".equals(normalizePrivacy(user))) {
            result.put("className", user.getClassName());
            result.put("cfHandle", user.getCfHandle());
            result.put("atcoderHandle", user.getAtcoderHandle());
            result.put("luoguHandle", user.getLuoguHandle());
            result.put("leetcodeHandle", user.getLeetcodeHandle());
            result.put("nowcoderHandle", user.getNowcoderHandle());
            result.put("qojHandle", user.getQojHandle());
        }
        return result;
    }

    private Map<String, Object> stats(Long userId) {
        List<SubmissionRecord> ac = submissionMapper.selectList(new LambdaQueryWrapper<SubmissionRecord>()
                .eq(SubmissionRecord::getUserId, userId)
                .eq(SubmissionRecord::getResult, "AC"));
        long solved = ac.stream().map(SubmissionRecord::getProblemId).distinct().count();
        List<ScoreEvent> events = scoreEventMapper.selectList(new LambdaQueryWrapper<ScoreEvent>()
                .eq(ScoreEvent::getUserId, userId));
        BigDecimal score = events.stream().map(ScoreEvent::getScore).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("solvedCount", solved, "submissionCount", ac.size(), "totalScore", score);
    }

    private List<Map<String, Object>> submissions(Long userId) {
        List<SubmissionRecord> records = submissionMapper.selectList(new LambdaQueryWrapper<SubmissionRecord>()
                .eq(SubmissionRecord::getUserId, userId)
                .eq(SubmissionRecord::getResult, "AC")
                .orderByDesc(SubmissionRecord::getSubmittedAt)
                .last("LIMIT 100"));
        Map<Long, Problem> problems = new LinkedHashMap<>();
        List<Long> ids = records.stream().map(SubmissionRecord::getProblemId).filter(Objects::nonNull).distinct().toList();
        if (!ids.isEmpty()) problemMapper.selectBatchIds(ids).forEach(problem -> problems.put(problem.getId(), problem));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SubmissionRecord record : records) {
            Problem problem = problems.get(record.getProblemId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", record.getId());
            row.put("platform", record.getPlatform());
            row.put("problemNo", record.getProblemNo());
            row.put("title", problem == null ? null : problem.getTitle());
            row.put("url", record.getSubmissionUrl());
            row.put("language", record.getCodeLanguage());
            row.put("code", record.getCodeContent());
            row.put("submittedAt", record.getSubmittedAt());
            result.add(row);
        }
        return result;
    }

    private Map<String, Object> award(Award award) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", award.getId());
        result.put("awardName", award.getAwardName());
        result.put("contestName", award.getContestName());
        result.put("awardLevel", award.getAwardLevel());
        result.put("awardedAt", award.getAwardedAt());
        result.put("certUrl", award.getCertUrl());
        result.put("note", award.getNote());
        return result;
    }
}
