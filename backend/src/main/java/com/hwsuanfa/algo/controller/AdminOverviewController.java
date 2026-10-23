package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.AuditLog;
import com.hwsuanfa.algo.entity.Award;
import com.hwsuanfa.algo.entity.ProblemList;
import com.hwsuanfa.algo.entity.ScoreEvent;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.AuditLogMapper;
import com.hwsuanfa.algo.mapper.AwardMapper;
import com.hwsuanfa.algo.mapper.ProblemListMapper;
import com.hwsuanfa.algo.mapper.ScoreEventMapper;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AssignmentService;
import com.hwsuanfa.algo.vo.AssignmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOverviewController {
    private final TeamMapper teams;
    private final UserMapper users;
    private final AwardMapper awards;
    private final ProblemListMapper problemLists;
    private final ScoreEventMapper scores;
    private final AuditLogMapper logs;
    private final AssignmentService assignments;

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview(@AuthenticationPrincipal LoginUser me) {
        requireOwner(me);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate from = today.minusDays(6);
        List<AssignmentVO> all = assignments.adminList("ALL", false);
        List<AssignmentVO> regular = all.stream().filter(a -> !"DAILY".equals(a.getType())).toList();
        List<AssignmentVO> daily = all.stream().filter(a -> "DAILY".equals(a.getType())).toList();
        Map<LocalDate, BigDecimal> days = new LinkedHashMap<>();
        for (int index = 0; index < 7; index++) days.put(from.plusDays(index), BigDecimal.ZERO);
        List<ScoreEvent> events = scores.selectList(new LambdaQueryWrapper<ScoreEvent>()
                .ge(ScoreEvent::getCreatedAt, from.atStartOfDay())
                .lt(ScoreEvent::getCreatedAt, today.plusDays(1).atStartOfDay()));
        for (ScoreEvent event : events) {
            if (event.getCreatedAt() != null && event.getScore() != null
                    && days.containsKey(event.getCreatedAt().toLocalDate())) {
                days.merge(event.getCreatedAt().toLocalDate(), event.getScore(), BigDecimal::add);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("teamCount", teams.selectCount(null));
        result.put("activeUsers", users.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, "ACTIVE").ge(User::getLastLoginAt, from.atStartOfDay())));
        result.put("assignmentCount", regular.size());
        result.put("dailyCount", daily.size());
        result.put("assignmentCompletionRate", completionRate(regular));
        result.put("dailyCompletionRate", completionRate(daily));
        result.put("pendingAwards", awards.selectCount(new LambdaQueryWrapper<Award>().eq(Award::getAuditStatus, "PENDING")));
        result.put("pendingProblemLists", problemLists.selectCount(new LambdaQueryWrapper<ProblemList>().eq(ProblemList::getAuditStatus, "PENDING")));
        List<Object> sum = scores.selectObjs(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScoreEvent>()
                .select("COALESCE(SUM(score), 0)"));
        result.put("totalScore", sum.isEmpty() || sum.getFirst() == null ? BigDecimal.ZERO : new BigDecimal(sum.getFirst().toString()));
        result.put("recentScores", days.entrySet().stream()
                .map(entry -> Map.of("date", entry.getKey().toString(), "score", entry.getValue())).toList());
        return ApiResult.ok(result);
    }

    @GetMapping("/logs")
    public ApiResult<List<Map<String, Object>>> logs(@RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Long beforeId, @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal LoginUser me) {
        requireOwner(me);
        LambdaQueryWrapper<AuditLog> query = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getId).last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        if (beforeId != null) query.lt(AuditLog::getId, beforeId);
        if (keyword != null && !keyword.isBlank()) {
            String text = keyword.trim();
            if (text.length() > 100) throw BizException.of(ErrorCode.BAD_REQUEST, "搜索词不能超过 100 字");
            query.and(q -> q.like(AuditLog::getOperatorName, text).or().like(AuditLog::getAction, text)
                    .or().like(AuditLog::getDetail, text));
        }
        return ApiResult.ok(logs.selectList(query).stream().map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("operatorName", log.getOperatorName());
            row.put("action", log.getAction());
            row.put("targetType", log.getTargetType());
            row.put("targetId", log.getTargetId());
            row.put("detail", log.getDetail());
            row.put("createdAt", log.getCreatedAt());
            return row;
        }).toList());
    }

    private double completionRate(List<AssignmentVO> items) {
        long target = 0;
        long completed = 0;
        for (AssignmentVO item : items) {
            if (item.getStats() == null) continue;
            target += item.getStats().getTargetUsers() == null ? 0 : item.getStats().getTargetUsers();
            completed += item.getStats().getCompletedUsers() == null ? 0 : item.getStats().getCompletedUsers();
        }
        return target == 0 ? 0 : BigDecimal.valueOf(completed * 100)
                .divide(BigDecimal.valueOf(target), 2, RoundingMode.HALF_UP).doubleValue();
    }

    private void requireOwner(LoginUser me) {
        if (me == null) throw BizException.of(ErrorCode.UNAUTHORIZED);
        if (!me.isSiteOwner()) throw BizException.of(ErrorCode.FORBIDDEN);
    }
}
