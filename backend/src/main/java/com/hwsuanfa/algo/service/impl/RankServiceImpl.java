package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.entity.Team;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.RankQueryMapper;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.service.RankService;
import com.hwsuanfa.algo.vo.RankVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 排行榜。
 *
 * 隐私规则（后端裁决，前端不参与过滤）：
 *   - 完全私密（PRIVATE）的用户不上榜
 *   - 部分私密/公开正常上榜（排行榜是校内激励，匿名参赛没有意义）
 * 同分同名次（1224 排名法）。
 */
@Service
@RequiredArgsConstructor
public class RankServiceImpl implements RankService {

    private final RankQueryMapper rankQueryMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;

    @Override
    public RankVO leaderboard() {
        return RankVO.builder()
                .users(userRank())
                .teams(teamRank())
                .build();
    }

    private List<RankVO.UserRow> userRank() {
        List<Map<String, Object>> rows = rankQueryMapper.selectUserLeaderboard();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> teamNames = activeTeamNameByUser();

        List<RankVO.UserRow> out = new ArrayList<>();
        int seq = 0;
        int lastRank = 0;
        BigDecimal lastScore = null;
        for (Map<String, Object> row : rows) {
            BigDecimal total = toBigDecimal(row.get("totalScore"));
            seq++;
            if (lastScore == null || total.compareTo(lastScore) != 0) {
                lastRank = seq;
                lastScore = total;
            }
            long userId = ((Number) row.get("userId")).longValue();
            out.add(RankVO.UserRow.builder()
                    .rank(lastRank)
                    .userId(userId)
                    .nickname((String) row.get("nickname"))
                    .className((String) row.get("className"))
                    .totalScore(total)
                    .solvedCount(intValue(row.get("solvedCount")))
                    .firstAc(intValue(row.get("firstAc")))
                    .dailyCount(intValue(row.get("dailyCount")))
                    .assignmentCount(intValue(row.get("assignmentCount")))
                    .contestCount(intValue(row.get("contestCount")))
                    .teamName(teamNames.get(userId))
                    .build());
        }
        return out;
    }

    private List<RankVO.TeamRow> teamRank() {
        List<Map<String, Object>> rows = rankQueryMapper.selectTeamLeaderboard();
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> leaderIds = rows.stream()
                .map(r -> (Number) r.get("leaderUserId"))
                .filter(Objects::nonNull)
                .map(Number::longValue)
                .collect(Collectors.toSet());
        Map<Long, String> leaderNames = leaderIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(leaderIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));

        List<RankVO.TeamRow> out = new ArrayList<>();
        int seq = 0;
        int lastRank = 0;
        BigDecimal lastScore = null;
        for (Map<String, Object> row : rows) {
            BigDecimal total = toBigDecimal(row.get("totalScore"));
            seq++;
            if (lastScore == null || total.compareTo(lastScore) != 0) {
                lastRank = seq;
                lastScore = total;
            }
            Number leaderValue = (Number) row.get("leaderUserId");
            Long leaderId = leaderValue == null ? null : leaderValue.longValue();
            int memberCount = intValue(row.get("memberCount"));
            out.add(RankVO.TeamRow.builder()
                    .rank(lastRank)
                    .teamId(((Number) row.get("teamId")).longValue())
                    .teamName((String) row.get("teamName"))
                    .leaderNickname(leaderId == null ? null : leaderNames.get(leaderId))
                    .memberCount(memberCount)
                    .totalScore(total)
                    .avgScore(memberCount == 0 ? BigDecimal.ZERO
                            : total.divide(BigDecimal.valueOf(memberCount), 1, RoundingMode.HALF_UP))
                    .build());
        }
        return out;
    }

    /** 用户 → 在队队伍名（多队时取第一个） */
    private Map<Long, String> activeTeamNameByUser() {
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getStatus, "ACTIVE"));
        if (memberships.isEmpty()) {
            return Map.of();
        }
        Set<Long> teamIds = memberships.stream()
                .map(TeamMember::getTeamId).collect(Collectors.toSet());
        Map<Long, Team> teams = teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        Map<Long, String> out = new HashMap<>();
        for (TeamMember tm : memberships) {
            Team t = teams.get(tm.getTeamId());
            if (t != null && "ACTIVE".equals(t.getStatus()) && !out.containsKey(tm.getUserId())) {
                out.put(tm.getUserId(), t.getName());
            }
        }
        return out;
    }

    private static int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value == null ? BigDecimal.ZERO
                : (value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString()));
    }
}
