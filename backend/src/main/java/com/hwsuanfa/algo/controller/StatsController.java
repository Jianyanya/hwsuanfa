package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.AssignmentProgress;
import com.hwsuanfa.algo.entity.AssignmentTarget;
import com.hwsuanfa.algo.entity.ScoreEvent;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.AssignmentProgressMapper;
import com.hwsuanfa.algo.mapper.AssignmentTargetMapper;
import com.hwsuanfa.algo.mapper.ScoreEventMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final AssignmentTargetMapper targetMapper;
    private final AssignmentProgressMapper progressMapper;
    private final ScoreEventMapper scoreEventMapper;

    @GetMapping("/teams/{teamId}/completion")
    public ApiResult<Map<String, Object>> completion(@PathVariable Long teamId,
                                                     @AuthenticationPrincipal LoginUser me) {
        List<TeamMember> members = members(teamId, me);
        List<Long> ids = members.stream().map(TeamMember::getUserId).toList();
        Map<Long, User> users = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> assignments = targetMapper.selectList(new LambdaQueryWrapper<AssignmentTarget>()
                        .eq(AssignmentTarget::getTeamId, teamId)).stream()
                .map(AssignmentTarget::getAssignmentId).collect(Collectors.toSet());
        Map<Long, List<AssignmentProgress>> progresses = ids.isEmpty() ? Map.of() : progressMapper.selectList(
                        new LambdaQueryWrapper<AssignmentProgress>().eq(AssignmentProgress::getTeamId, teamId)
                                .in(AssignmentProgress::getUserId, ids))
                .stream().filter(p -> assignments.isEmpty() || assignments.contains(p.getAssignmentId()))
                .collect(Collectors.groupingBy(AssignmentProgress::getUserId));

        List<Map<String, Object>> rows = new ArrayList<>();
        int totalCompleted = 0;
        int totalProblems = 0;
        for (TeamMember member : members) {
            List<AssignmentProgress> items = progresses.getOrDefault(member.getUserId(), List.of());
            int completed = items.stream().map(AssignmentProgress::getCompletedCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
            int total = items.stream().map(AssignmentProgress::getTotalCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
            totalCompleted += completed;
            totalProblems += total;
            User user = users.get(member.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", member.getUserId());
            row.put("nickname", user == null ? "未知" : user.getNickname());
            row.put("completedCount", completed);
            row.put("totalCount", total);
            row.put("completionRate", total == 0 ? 0 : Math.round(completed * 10000d / total) / 100d);
            rows.add(row);
        }
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("nickname"))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("teamId", teamId);
        body.put("assignmentCount", assignments.size());
        body.put("completedCount", totalCompleted);
        body.put("totalCount", totalProblems);
        body.put("members", rows);
        return ApiResult.ok(body);
    }

    @GetMapping("/score-trend")
    public ApiResult<Map<String, Object>> scoreTrend(@RequestParam(required = false) Long teamId,
                                                     @RequestParam(defaultValue = "30") int days,
                                                     @AuthenticationPrincipal LoginUser me) {
        int safeDays = Math.min(90, Math.max(7, days));
        List<Long> userIds = teamId == null ? List.of(me.getId())
                : members(teamId, me).stream().map(TeamMember::getUserId).toList();
        LocalDate start = LocalDate.now().minusDays(safeDays - 1L);
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        for (int i = 0; i < safeDays; i++) totals.put(start.plusDays(i), BigDecimal.ZERO);
        if (!userIds.isEmpty()) {
            List<ScoreEvent> events = scoreEventMapper.selectList(new LambdaQueryWrapper<ScoreEvent>()
                    .in(ScoreEvent::getUserId, userIds).ge(ScoreEvent::getOccurredAt, start.atStartOfDay()));
            for (ScoreEvent event : events) {
                if (event.getOccurredAt() == null || event.getScore() == null) continue;
                LocalDate date = event.getOccurredAt().toLocalDate();
                if (totals.containsKey(date)) totals.put(date, totals.get(date).add(event.getScore()));
            }
        }
        List<Map<String, Object>> points = totals.entrySet().stream()
                .map(entry -> Map.<String, Object>of("date", entry.getKey(), "score", entry.getValue())).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("teamId", teamId);
        body.put("days", safeDays);
        body.put("points", points);
        return ApiResult.ok(body);
    }

    private List<TeamMember> members(Long teamId, LoginUser me) {
        if (me == null) throw BizException.of(ErrorCode.UNAUTHORIZED);
        List<TeamMember> members = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getStatus, "ACTIVE"));
        if (!me.isSiteOwner() && members.stream().noneMatch(member -> Objects.equals(member.getUserId(), me.getId()))) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
        return members;
    }
}
