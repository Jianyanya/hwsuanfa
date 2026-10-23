package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.dto.ContestLogRequest;
import com.hwsuanfa.algo.dto.ContestRequest;
import com.hwsuanfa.algo.entity.Contest;
import com.hwsuanfa.algo.entity.ContestLog;
import com.hwsuanfa.algo.entity.Team;
import com.hwsuanfa.algo.entity.TeamContest;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.entity.UserContest;
import com.hwsuanfa.algo.mapper.ContestLogMapper;
import com.hwsuanfa.algo.mapper.ContestMapper;
import com.hwsuanfa.algo.mapper.UserContestMapper;
import com.hwsuanfa.algo.mapper.TeamContestMapper;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.service.ContestService;
import com.hwsuanfa.algo.service.ScoreService;
import com.hwsuanfa.algo.service.standings.StandingsFetcher;
import com.hwsuanfa.algo.service.standings.UserResultsFetcher;
import com.hwsuanfa.algo.vo.ContestVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContestServiceImpl implements ContestService {

    private static final String SEL_SELECTED = "SELECTED";
    private static final String SEL_CANCELLED = "CANCELLED";

    private static final String LOG_PENDING = "PENDING";
    private static final String LOG_CONFIRMED = "CONFIRMED";

    private static final String PART_TEAM = "TEAM";
    private static final String PART_USER = "USER";

    private static final Set<String> LEVELS = Set.of("S", "A", "B", "C", "OTHER");

    private static final Map<String, String> PLATFORM_LABEL = Map.of(
            "CODEFORCES", "Codeforces", "ATCODER", "AtCoder", "LUOGU", "洛谷",
            "NOWCODER", "牛客", "LEETCODE", "LeetCode", "OTHER", "其他");

    private final ContestMapper contestMapper;
    private final TeamContestMapper teamContestMapper;
    private final ContestLogMapper logMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ScoreService scoreService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final UserContestMapper userContestMapper;
    private final List<StandingsFetcher> standingsFetchers;
    private final List<UserResultsFetcher> userResultsFetchers;

    /* ==================== 站主 ==================== */

    @Override
    @Transactional
    public ContestVO create(ContestRequest req, LoginUser operator) {
        Contest c = new Contest();
        apply(c, req);
        c.setCreatedBy(operator.getId());
        contestMapper.insert(c);

        auditLogService.record(operator, "CONTEST_CREATE", "CONTEST", c.getId(),
                null, Map.of("level", c.getLevel()), "登记比赛：" + c.getName());
        log.info("站主 {} 登记比赛「{}」（{} 级）", operator.getNickname(), c.getName(), c.getLevel());
        return buildVO(c, operator, true);
    }

    @Override
    @Transactional
    public ContestVO update(Long id, ContestRequest req, LoginUser operator) {
        Contest c = requireContest(id);
        apply(c, req);
        contestMapper.updateById(c);

        auditLogService.record(operator, "CONTEST_UPDATE", "CONTEST", id,
                null, null, "编辑比赛：" + c.getName());
        return buildVO(requireContest(id), operator, true);
    }

    @Override
    @Transactional
    public void delete(Long id, LoginUser operator) {
        Contest c = requireContest(id);

        long selected = teamContestMapper.selectCount(new LambdaQueryWrapper<TeamContest>()
                .eq(TeamContest::getContestId, id));
        long individuals = userContestMapper.selectCount(new LambdaQueryWrapper<UserContest>()
                .eq(UserContest::getContestId, id));
        long logs = logMapper.selectCount(new LambdaQueryWrapper<ContestLog>()
                .eq(ContestLog::getContestId, id));
        if (selected > 0 || individuals > 0 || logs > 0) {
            throw BizException.of(ErrorCode.CONFLICT,
                    "该比赛已有报名或比赛日志，不能删除，请保留历史记录。");
        }

        auditLogService.record(operator, "CONTEST_DELETE", "CONTEST", id,
                Map.of("name", c.getName()), null, "删除比赛：" + c.getName());
        // contest_log 有外键指向 contest，没有 CASCADE，先清日志
        logMapper.delete(new LambdaQueryWrapper<ContestLog>().eq(ContestLog::getContestId, id));
        contestMapper.deleteById(id);
    }

    @Override
    public List<ContestVO> adminList() {
        return contestMapper.selectList(new LambdaQueryWrapper<Contest>()
                        .orderByDesc(Contest::getStartAt)
                        .orderByDesc(Contest::getId))
                .stream().map(c -> buildVO(c, null, false)).toList();
    }

    /* ==================== 队员 / 队长 ==================== */

    @Override
    public List<ContestVO> list(LoginUser me) {
        return contestMapper.selectList(new LambdaQueryWrapper<Contest>()
                        .orderByDesc(Contest::getStartAt)
                        .orderByDesc(Contest::getId))
                .stream().map(c -> buildVO(c, me, false)).toList();
    }

    @Override
    public ContestVO detail(Long id, LoginUser me) {
        return buildVO(requireContest(id), me, true);
    }

    @Override
    @Transactional
    public ContestVO selectParticipation(Long contestId, List<Long> teamIds, LoginUser me) {
        Contest c = requireContest(contestId);
        List<Long> ids = teamIds == null ? List.of() : teamIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "请选择要参赛的队伍");
        }

        for (Long teamId : ids) {
            // 只有本队队长或站主能替队伍做决定
            if (!me.isSiteOwner() && !isTeamLeader(teamId, me.getId())) {
                throw BizException.of(ErrorCode.FORBIDDEN, "只有本队队长可以决定是否参赛");
            }
        }

        for (Long teamId : ids) {
            TeamContest existing = teamContestMapper.selectOne(new LambdaQueryWrapper<TeamContest>()
                    .eq(TeamContest::getTeamId, teamId)
                    .eq(TeamContest::getContestId, contestId));
            if (existing != null) {
                if (SEL_SELECTED.equals(existing.getStatus())) {
                    // 重复选择不算错（幂等），只是不发分
                    continue;
                }
                // 之前取消过 → 恢复，但不补发参赛分（积分是「首次选择」时发的）
                TeamContest patch = new TeamContest();
                patch.setId(existing.getId());
                patch.setStatus(SEL_SELECTED);
                patch.setSelectedBy(me.getId());
                teamContestMapper.updateById(patch);
                continue;
            }

            TeamContest tc = new TeamContest();
            tc.setTeamId(teamId);
            tc.setContestId(contestId);
            tc.setSelectedBy(me.getId());
            tc.setStatus(SEL_SELECTED);
            try {
                teamContestMapper.insert(tc);
            } catch (DuplicateKeyException e) {
                // 并发重复选择由唯一索引兜底
                continue;
            }

            // 参赛积分：按比赛等级发给该队全体在队成员，幂等键带用户 id
            grantJoinScore(c, teamId);
        }

        auditLogService.record(me, "CONTEST_SELECT", "CONTEST", contestId,
                null, Map.of("teams", ids.size()), "选择参赛：" + c.getName());
        return buildVO(c, me, true);
    }

    /** 参赛分 = 全体在队成员各拿一份，按比赛等级取值（CONTEST_JOIN_S/A/B/C） */
    private void grantJoinScore(Contest c, Long teamId) {
        BigDecimal fallback = scoreService.resolveRuleScore(
                "CONTEST_JOIN_" + c.getLevel(), c.getLevel(), BigDecimal.ZERO);
        List<Long> memberIds = activeMemberIds(teamId);
        for (Long uid : memberIds) {
            scoreService.grant(uid, "CONTEST_JOIN", c.getLevel(), fallback,
                    "CONTEST", c.getId(),
                    "参加 " + c.getLevel() + " 级比赛「" + c.getName() + "」",
                    "contest_join:" + c.getId() + ":" + uid);
        }
    }

    @Override
    @Transactional
    public ContestVO cancelParticipation(Long contestId, Long teamId, LoginUser me) {
        Contest c = requireContest(contestId);
        if (!me.isSiteOwner() && !isTeamLeader(teamId, me.getId())) {
            throw BizException.of(ErrorCode.FORBIDDEN, "只有本队队长可以取消参赛");
        }
        TeamContest tc = teamContestMapper.selectOne(new LambdaQueryWrapper<TeamContest>()
                .eq(TeamContest::getTeamId, teamId)
                .eq(TeamContest::getContestId, contestId));
        if (tc == null || !SEL_SELECTED.equals(tc.getStatus())) {
            throw BizException.of(ErrorCode.CONTEST_NOT_SELECTED);
        }
        TeamContest patch = new TeamContest();
        patch.setId(tc.getId());
        patch.setStatus(SEL_CANCELLED);
        patch.setSelectedBy(me.getId());
        teamContestMapper.updateById(patch);

        auditLogService.record(me, "CONTEST_CANCEL", "CONTEST", contestId,
                Map.of("teamId", teamId), null, "取消参赛：" + c.getName());
        return buildVO(c, me, true);
    }

    @Override
    @Transactional
    public ContestVO submitLog(Long contestId, ContestLogRequest req, LoginUser me) {
        Contest c = requireContest(contestId);

        // 个人成绩：本人填自己的，或站主代填
        if (PART_USER.equals(normalizeParticipantType(req.getParticipantType()))) {
            Long uid = req.getUserId() == null ? me.getId() : req.getUserId();
            if (!me.isSiteOwner() && !Objects.equals(uid, me.getId())) {
                throw BizException.of(ErrorCode.FORBIDDEN, "只能填写自己的比赛成绩");
            }
            UserContest uc = userContestMapper.selectOne(new LambdaQueryWrapper<UserContest>()
                    .eq(UserContest::getUserId, uid)
                    .eq(UserContest::getContestId, contestId)
                    .eq(UserContest::getStatus, SEL_SELECTED));
            if (uc == null) {
                throw BizException.of(ErrorCode.CONTEST_NOT_SELECTED, "该选手还没有以个人身份报名这场比赛");
            }
            com.hwsuanfa.algo.common.StandingsRow row = new com.hwsuanfa.algo.common.StandingsRow(
                    "", req.getRankResult() == null ? "" : req.getRankResult(),
                    req.getAcCount() == null ? 0 : req.getAcCount(),
                    req.getPenaltyMinutes() == null ? 0 : req.getPenaltyMinutes(),
                    req.getDurationSeconds() == null ? 0 : req.getDurationSeconds());
            upsertScore(c, PART_USER, null, uid, row);

            auditLogService.record(me, "CONTEST_LOG_SUBMIT", "CONTEST", contestId,
                    null, Map.of("participant", "USER"), "提交个人比赛成绩：" + c.getName());
            return buildVO(c, me, true);
        }

        Long teamId = req.getTeamId();
        if (teamId == null) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "队伍参赛必须指定队伍");
        }

        // 提交人必须是该队成员（队长也是成员），站主代填除外
        if (!me.isSiteOwner() && !isTeamMember(teamId, me.getId())) {
            throw BizException.of(ErrorCode.NOT_CONTEST_PARTICIPANT);
        }
        // 该队必须真的选了参赛
        TeamContest tc = teamContestMapper.selectOne(new LambdaQueryWrapper<TeamContest>()
                .eq(TeamContest::getTeamId, teamId)
                .eq(TeamContest::getContestId, contestId)
                .eq(TeamContest::getStatus, SEL_SELECTED));
        if (tc == null) {
            throw BizException.of(ErrorCode.CONTEST_NOT_SELECTED, "该队伍还没有选择参加这场比赛");
        }

        // 参赛成员必须都是该队在队成员
        List<Long> memberIds = req.getMemberIds() == null ? List.of()
                : req.getMemberIds().stream().distinct().toList();
        Set<Long> active = new HashSet<>(activeMemberIds(teamId));
        if (memberIds.isEmpty() || !active.containsAll(memberIds)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "参赛成员必须是本队在队成员");
        }
        // 已个人报名的成员不占队伍名额（他们的成绩独立展示）
        Set<Long> individuallyJoined = userContestMapper.selectList(
                        new LambdaQueryWrapper<UserContest>()
                                .eq(UserContest::getContestId, contestId)
                                .eq(UserContest::getStatus, SEL_SELECTED))
                .stream().map(UserContest::getUserId).collect(Collectors.toSet());
        memberIds = memberIds.stream()
                .filter(uid -> !individuallyJoined.contains(uid)).toList();
        if (memberIds.isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST,
                    "该队成员都已个人报名，没有可计入队伍名单的成员");
        }

        boolean autoConfirm = me.isSiteOwner() || isTeamLeader(teamId, me.getId());

        ContestLog logEntry = new ContestLog();
        logEntry.setContestId(contestId);
        logEntry.setParticipantType(PART_TEAM);
        logEntry.setTeamId(teamId);
        logEntry.setMemberIds(toJson(memberIds));
        logEntry.setRankResult(req.getRankResult());
        logEntry.setSolvedCount(req.getSolvedCount());
        // 不带这三样站内排名就算不了（排序依据：AC 降序 → 罚时升序 → 用时升序）
        logEntry.setAcCount(req.getAcCount());
        logEntry.setPenaltyMinutes(req.getPenaltyMinutes());
        logEntry.setDurationSeconds(req.getDurationSeconds());
        logEntry.setSummary(req.getSummary());
        logEntry.setProblemListId(req.getProblemListId());
        logEntry.setStatus(autoConfirm ? LOG_CONFIRMED : LOG_PENDING);
        logEntry.setCreatedBy(me.getId());
        ContestLog existing = logMapper.selectOne(new LambdaQueryWrapper<ContestLog>()
                .eq(ContestLog::getContestId, contestId)
                .eq(ContestLog::getParticipantType, PART_TEAM)
                .eq(ContestLog::getTeamId, teamId)
                .orderByDesc(ContestLog::getId).last("LIMIT 1"));
        if (existing != null) {
            if (LOG_CONFIRMED.equals(existing.getStatus()) && !autoConfirm) {
                throw BizException.of(ErrorCode.FORBIDDEN, "已确认的比赛日志只能由队长或站主修改");
            }
            logEntry.setId(existing.getId());
            logMapper.updateById(logEntry);
        } else {
            logMapper.insert(logEntry);
        }

        if (autoConfirm) {
            grantRankScore(c, logEntry, memberIds);
        }

        auditLogService.record(me, "CONTEST_LOG_SUBMIT", "CONTEST", contestId,
                null, Map.of("teamId", teamId, "confirmed", autoConfirm),
                (autoConfirm ? "提交并确认比赛日志：" : "提交比赛日志（待确认）：") + c.getName());
        return buildVO(c, me, true);
    }

    @Override
    @Transactional
    public ContestVO confirmLog(Long contestId, Long logId, LoginUser me) {
        Contest c = requireContest(contestId);
        ContestLog logEntry = logMapper.selectById(logId);
        if (logEntry == null || !logEntry.getContestId().equals(contestId)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "比赛日志不存在");
        }
        if (!me.isSiteOwner() && !isTeamLeader(logEntry.getTeamId(), me.getId())) {
            throw BizException.of(ErrorCode.FORBIDDEN, "只有本队队长可以确认比赛日志");
        }
        if (LOG_CONFIRMED.equals(logEntry.getStatus())) {
            throw BizException.of(ErrorCode.CONTEST_LOG_CONFIRMED);
        }

        ContestLog patch = new ContestLog();
        patch.setId(logId);
        patch.setStatus(LOG_CONFIRMED);
        patch.setConfirmedBy(me.getId());
        logMapper.updateById(patch);

        grantRankScore(c, logEntry, fromJson(logEntry.getMemberIds()));

        auditLogService.record(me, "CONTEST_LOG_CONFIRM", "CONTEST", contestId,
                null, Map.of("logId", logId), "确认比赛日志：" + c.getName());
        return buildVO(c, me, true);
    }

    private void grantRankScore(Contest c, ContestLog logEntry, List<Long> memberIds) {
        for (Long uid : teamParticipantIds(c.getId(), memberIds)) {
            scoreService.grantContestRank(uid, c.getId(),
                    "比赛「" + c.getName() + "」取得名次 "
                            + (logEntry.getRankResult() == null ? "" : logEntry.getRankResult()));
        }
    }

    @Override
    @Transactional
    public void deleteLog(Long contestId, Long logId, LoginUser operator) {
        Contest c = requireContest(contestId);
        ContestLog logEntry = logMapper.selectById(logId);
        if (logEntry == null || !logEntry.getContestId().equals(contestId)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "比赛日志不存在");
        }
        if (LOG_CONFIRMED.equals(logEntry.getStatus())) {
            // 已确认的日志发过名次分，删掉会让积分流水指向不存在的对象
            throw BizException.of(ErrorCode.CONFLICT, "已确认的比赛日志不能删除");
        }
        auditLogService.record(operator, "CONTEST_LOG_DELETE", "CONTEST", contestId,
                null, null, "删除比赛日志：" + c.getName());
        logMapper.deleteById(logId);
    }

    /* ==================== 视图组装 ==================== */

    private ContestVO buildVO(Contest c, LoginUser viewer, boolean withLogs) {
        LocalDateTime now = LocalDateTime.now();
        String ts;
        if (c.getStartAt() != null && now.isBefore(c.getStartAt())) {
            ts = "NOT_STARTED";
        } else if (c.getEndAt() != null && now.isAfter(c.getEndAt())) {
            ts = "ENDED";
        } else {
            ts = "IN_PROGRESS";
        }

        List<TeamContest> selections = teamContestMapper.selectList(
                new LambdaQueryWrapper<TeamContest>()
                        .eq(TeamContest::getContestId, c.getId())
                        .eq(TeamContest::getStatus, SEL_SELECTED));
        List<Long> teamIds = selections.stream().map(TeamContest::getTeamId).distinct().toList();

        List<ContestVO.TeamRef> teams = Collections.emptyList();
        if (!teamIds.isEmpty()) {
            Map<Long, Team> tmap = teamMapper.selectBatchIds(teamIds).stream()
                    .collect(Collectors.toMap(Team::getId, Function.identity()));
            teams = teamIds.stream().map(tmap::get).filter(Objects::nonNull)
                    .map(t -> ContestVO.TeamRef.builder().id(t.getId()).name(t.getName()).build())
                    .toList();
        }

        Boolean mySelected = null;
        List<String> myRoles = null;
        Boolean individualAllowed = null;
        boolean meTeamJoined = false;
        boolean meIndividualJoined = false;
        if (viewer != null) {
            Set<Long> myTeams = myTeamIdsOf(viewer.getId());
            meTeamJoined = teamIds.stream().anyMatch(myTeams::contains);
            mySelected = meTeamJoined;

            meIndividualJoined = userContestMapper.selectCount(
                    new LambdaQueryWrapper<UserContest>()
                            .eq(UserContest::getUserId, viewer.getId())
                            .eq(UserContest::getContestId, c.getId())
                            .eq(UserContest::getStatus, SEL_SELECTED)) > 0;

            myRoles = new ArrayList<>();
            if (meTeamJoined) myRoles.add(PART_TEAM);
            if (meIndividualJoined) myRoles.add(PART_USER);

            // 规则：本队已报名 → 成员不能再以个人身份参加
            individualAllowed = !meIndividualJoined && !meTeamJoined;
        }

        BigDecimal joinScore = scoreService.resolveRuleScore(
                "CONTEST_JOIN_" + c.getLevel(), c.getLevel(), BigDecimal.ZERO);

        List<ContestVO.LogVO> logs = Collections.emptyList();
        if (withLogs) {
            List<ContestLog> entries = logMapper.selectList(new LambdaQueryWrapper<ContestLog>()
                    .eq(ContestLog::getContestId, c.getId())
                    .orderByDesc(ContestLog::getId));
            logs = buildLogVOs(c.getId(), entries);
        }

        return ContestVO.builder()
                .id(c.getId())
                .name(c.getName())
                .platform(c.getPlatform())
                .url(c.getUrl())
                .level(c.getLevel())
                .joinScore(joinScore)
                .startAt(c.getStartAt())
                .endAt(c.getEndAt())
                .createdAt(c.getCreatedAt())
                .timeStatus(ts)
                .secondsUntilStart(until(c.getStartAt(), now))
                .secondsUntilEnd(until(c.getEndAt(), now))
                .teams(teams)
                .teamCount(teams.size())
                .myTeamSelected(mySelected)
                .myRoles(myRoles)
                .individualJoinAllowed(individualAllowed)
                .logs(logs)
                .build();
    }

    /**
     * 组装日志视图并计算站内排名。
     *
     * 站内排名规则：AC 数降序 → 罚时升序 → 用时升序，**队伍与个人混在一起排**。
     * 没填成绩的参赛者不参与排名（排最后，不带名次号）。
     *
     * 队伍参赛的名单要**剔除已个人报名的成员**（用户定的规则：
     * 成员个人参加了，队伍仍可报名，但该成员的成绩独立展示，不占队伍名额）。
     */
    private List<ContestVO.LogVO> buildLogVOs(Long contestId, List<ContestLog> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        // ⚠️ selectBatchIds 传空列表会生成 IN () 非法 SQL，必须先判空
        List<Long> entryTeamIds = entries.stream()
                .map(ContestLog::getTeamId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Team> teams = entryTeamIds.isEmpty() ? Collections.emptyMap()
                : teamMapper.selectBatchIds(entryTeamIds).stream()
                        .collect(Collectors.toMap(Team::getId, Function.identity()));

        // 已个人报名的成员：从队伍名单里剔除
        Set<Long> individuallyJoined = userContestMapper.selectList(
                        new LambdaQueryWrapper<UserContest>()
                                .eq(UserContest::getContestId, contestId)
                                .eq(UserContest::getStatus, SEL_SELECTED))
                .stream().map(UserContest::getUserId).collect(Collectors.toSet());

        Set<Long> userIds = new LinkedHashSet<>();
        for (ContestLog e : entries) {
            if (e.getCreatedBy() != null) userIds.add(e.getCreatedBy());
            if (PART_USER.equals(e.getParticipantType()) && e.getUserId() != null) {
                userIds.add(e.getUserId());
            }
            for (Long id : fromJson(e.getMemberIds())) userIds.add(id);
        }
        Map<Long, User> users = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ContestVO.LogVO> vos = entries.stream().map(e -> {
            boolean isUser = PART_USER.equals(e.getParticipantType());
            User me = isUser && e.getUserId() != null ? users.get(e.getUserId()) : null;
            List<ContestVO.MemberRef> members = isUser ? Collections.emptyList()
                    : fromJson(e.getMemberIds()).stream()
                            // 剔除已个人报名的成员
                            .filter(uid -> !individuallyJoined.contains(uid))
                            .map(uid -> {
                                User u = users.get(uid);
                                return ContestVO.MemberRef.builder()
                                        .id(uid).nickname(u == null ? "#" + uid : u.getNickname()).build();
                            }).toList();

            return ContestVO.LogVO.builder()
                    .id(e.getId())
                    .participantType(e.getParticipantType())
                    .teamId(isUser ? null : e.getTeamId())
                    .teamName(isUser ? null
                            : (teams.get(e.getTeamId()) == null ? null : teams.get(e.getTeamId()).getName()))
                    .userId(isUser ? e.getUserId() : null)
                    .userNickname(isUser ? (me == null ? null : me.getNickname()) : null)
                    .userAvatar(isUser ? (me == null ? null : me.getAvatar()) : null)
                    .members(members)
                    .rankResult(e.getRankResult())
                    .solvedCount(e.getSolvedCount())
                    .acCount(e.getAcCount())
                    .durationSeconds(e.getDurationSeconds())
                    .penaltyMinutes(e.getPenaltyMinutes())
                    .summary(e.getSummary())
                    .problemListId(e.getProblemListId())
                    .status(e.getStatus())
                    .createdBy(e.getCreatedBy() == null ? null : nicknameOf(users.get(e.getCreatedBy())))
                    .createdAt(e.getCreatedAt())
                    .build();
        }).toList();

        // 站内排名：只有已确认且填了成绩的参与排名
        List<ContestVO.LogVO> ranked = vos.stream()
                .filter(v -> LOG_CONFIRMED.equals(v.getStatus()))
                .filter(v -> v.getAcCount() != null)
                .sorted((a, b) -> {
                    int d = Integer.compare(b.getAcCount(), a.getAcCount());
                    if (d != 0) return d;
                    int pa = a.getPenaltyMinutes() == null ? Integer.MAX_VALUE : a.getPenaltyMinutes();
                    int pb = b.getPenaltyMinutes() == null ? Integer.MAX_VALUE : b.getPenaltyMinutes();
                    d = Integer.compare(pa, pb);
                    if (d != 0) return d;
                    int da = a.getDurationSeconds() == null ? Integer.MAX_VALUE : a.getDurationSeconds();
                    int db = b.getDurationSeconds() == null ? Integer.MAX_VALUE : b.getDurationSeconds();
                    return Integer.compare(da, db);
                }).toList();
        Map<Long, Integer> rankById = new java.util.HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            rankById.put(ranked.get(i).getId(), i + 1);
        }
        return vos.stream().map(v -> {
            Integer r = rankById.get(v.getId());
            return r == null ? v : ContestVO.LogVO.builder()
                    .id(v.getId()).participantType(v.getParticipantType())
                    .teamId(v.getTeamId()).teamName(v.getTeamName())
                    .userId(v.getUserId()).userNickname(v.getUserNickname()).userAvatar(v.getUserAvatar())
                    .members(v.getMembers()).rankResult(v.getRankResult())
                    .solvedCount(v.getSolvedCount()).acCount(v.getAcCount())
                    .durationSeconds(v.getDurationSeconds()).penaltyMinutes(v.getPenaltyMinutes())
                    .siteRank(r).summary(v.getSummary()).problemListId(v.getProblemListId())
                    .status(v.getStatus()).createdBy(v.getCreatedBy()).createdAt(v.getCreatedAt())
                    .build();
        }).toList();
    }

    private String nicknameOf(User u) {
        return u == null ? null : u.getNickname();
    }


    /* ==================== 个人参赛 ==================== */

    @Override
    @Transactional
    public ContestVO joinIndividually(Long contestId, LoginUser me) {
        Contest c = requireContest(contestId);

        // 规则（用户定的）：本队已经报名这场比赛时，成员不能再以个人身份参加
        Set<Long> myTeams = myTeamIdsOf(me.getId());
        Long teamJoined = teamContestMapper.selectList(new LambdaQueryWrapper<TeamContest>()
                        .eq(TeamContest::getContestId, contestId)
                        .eq(TeamContest::getStatus, SEL_SELECTED))
                .stream().map(TeamContest::getTeamId)
                .filter(myTeams::contains).findFirst().orElse(null);
        if (teamJoined != null) {
            throw BizException.of(ErrorCode.CONFLICT,
                    "你的队伍已经报名这场比赛，成员不能再以个人身份参加");
        }

        UserContest existing = userContestMapper.selectOne(new LambdaQueryWrapper<UserContest>()
                .eq(UserContest::getUserId, me.getId())
                .eq(UserContest::getContestId, contestId));
        if (existing != null && SEL_SELECTED.equals(existing.getStatus())) {
            return buildVO(c, me, true);   // 重复报名幂等
        }

        if (existing != null) {
            UserContest patch = new UserContest();
            patch.setId(existing.getId());
            patch.setStatus(SEL_SELECTED);
            userContestMapper.updateById(patch);
        } else {
            UserContest uc = new UserContest();
            uc.setUserId(me.getId());
            uc.setContestId(contestId);
            User meUser = userMapper.selectById(me.getId());
            uc.setExternalHandle(handleOf(meUser, c.getPlatform()));
            uc.setStatus(SEL_SELECTED);
            try {
                userContestMapper.insert(uc);
            } catch (DuplicateKeyException e) {
                // 并发重复报名由唯一索引兜底
            }
        }

        // 个人参赛同样拿参赛分；幂等键保证「先个人后队伍」或「先队伍后个人」都只发一次
        BigDecimal fallback = scoreService.resolveRuleScore(
                "CONTEST_JOIN_" + c.getLevel(), c.getLevel(), BigDecimal.ZERO);
        scoreService.grant(me.getId(), "CONTEST_JOIN", c.getLevel(), fallback,
                "CONTEST", c.getId(),
                "个人参加 " + c.getLevel() + " 级比赛「" + c.getName() + "」",
                "contest_join:" + c.getId() + ":" + me.getId());

        auditLogService.record(me, "CONTEST_INDIVIDUAL_JOIN", "CONTEST", contestId,
                null, null, "个人报名比赛：" + c.getName());
        return buildVO(c, me, true);
    }

    /** 从用户资料里取该平台的 handle，没有则用昵称占位 */
    private String handleOf(User u, String platform) {
        if (u == null) return null;
        return switch (platform == null ? "" : platform.toUpperCase()) {
            case "CODEFORCES" -> u.getCfHandle() != null ? u.getCfHandle() : u.getNickname();
            case "ATCODER" -> u.getAtcoderHandle() != null ? u.getAtcoderHandle() : u.getNickname();
            case "LUOGU" -> u.getLuoguHandle() != null ? u.getLuoguHandle() : u.getNickname();
            case "NOWCODER" -> u.getNowcoderHandle() != null ? u.getNowcoderHandle() : u.getNickname();
            default -> u.getNickname();
        };
    }

    @Override
    @Transactional
    public ContestVO cancelIndividualJoin(Long contestId, LoginUser me) {
        Contest c = requireContest(contestId);
        UserContest uc = userContestMapper.selectOne(new LambdaQueryWrapper<UserContest>()
                .eq(UserContest::getUserId, me.getId())
                .eq(UserContest::getContestId, contestId));
        if (uc == null || !SEL_SELECTED.equals(uc.getStatus())) {
            throw BizException.of(ErrorCode.CONTEST_NOT_SELECTED, "你还没有以个人身份报名这场比赛");
        }
        UserContest patch = new UserContest();
        patch.setId(uc.getId());
        patch.setStatus(SEL_CANCELLED);
        userContestMapper.updateById(patch);

        auditLogService.record(me, "CONTEST_INDIVIDUAL_CANCEL", "CONTEST", contestId,
                null, null, "取消个人报名：" + c.getName());
        return buildVO(c, me, true);
    }

    /* ==================== 榜单 ==================== */

    @Override
    @Transactional
    public ContestVO syncStandings(Long contestId, LoginUser operator) {
        Contest c = requireContest(contestId);

        StandingsFetcher fetcher = standingsFetchers.stream()
                .filter(f -> f.supports(c.getPlatform())).findFirst().orElse(null);
        if (fetcher == null) {
            // AtCoder 走「按人抓成绩」（下方），其余没有公开接口的回落手动录入
            UserResultsFetcher perUser = userResultsFetchers.stream()
                    .filter(f -> f.supports(c.getPlatform())).findFirst().orElse(null);
            if (perUser == null) {
                throw BizException.of(ErrorCode.BAD_REQUEST,
                        PLATFORM_LABEL.getOrDefault(c.getPlatform(), c.getPlatform())
                                + " 没有公开的榜单接口，请手动录入成绩");
            }
            int matchedUser = 0;
            for (UserContest uc : joinedIndividuals(contestId)) {
                User u = userMapper.selectById(uc.getUserId());
                String handle = uc.getExternalHandle() != null ? uc.getExternalHandle()
                        : (u == null ? null : handleOf(u, c.getPlatform()));
                com.hwsuanfa.algo.common.StandingsRow row =
                        perUser.fetchUserResult(c.getExternalId(), handle);
                if (row != null) {
                    upsertScore(c, PART_USER, null, uc.getUserId(), row);
                    matchedUser++;
                }
            }
            // 队伍参赛的 AtCoder 抓不到（队伍账号成绩不在个人 results 里），提示补录
            auditLogService.record(operator, "CONTEST_STANDINGS_SYNC", "CONTEST", contestId,
                    null, Map.of("matched", matchedUser), "按人同步官方成绩：" + c.getName());
            if (matchedUser == 0) {
                throw BizException.of(ErrorCode.BAD_REQUEST,
                        "没有抓到任何参赛者的成绩。请检查个人中心绑定的用户名是否正确、"
                                + "该选手是否参加了这场比赛。");
            }
            return buildVO(c, operator, true);
        }
        List<com.hwsuanfa.algo.common.StandingsRow> rows = fetcher.fetch(c.getExternalId());
        if (rows.isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "官方榜单为空");
        }
        Map<String, com.hwsuanfa.algo.common.StandingsRow> byHandle = rows.stream()
                .collect(Collectors.toMap(r -> r.handle().toLowerCase(), r -> r, (a, b) -> a));

        int matched = 0;
        // 队伍参赛：按队伍的 handle 匹配
        for (TeamContest tc : joinedTeams(contestId)) {
            Team t = teamMapper.selectById(tc.getTeamId());
            String handle = t == null ? null : t.getName();
            com.hwsuanfa.algo.common.StandingsRow row = handle == null ? null : byHandle.get(handle.toLowerCase());
            if (row != null) {
                upsertScore(c, PART_TEAM, tc.getTeamId(), null, row);
                matched++;
            }
        }
        // 个人参赛：按用户的平台 handle 匹配
        for (UserContest uc : joinedIndividuals(contestId)) {
            User u = userMapper.selectById(uc.getUserId());
            String handle = uc.getExternalHandle() != null ? uc.getExternalHandle()
                    : (u == null ? null : handleOf(u, c.getPlatform()));
            com.hwsuanfa.algo.common.StandingsRow row = handle == null ? null : byHandle.get(handle.toLowerCase());
            if (row != null) {
                upsertScore(c, PART_USER, null, uc.getUserId(), row);
                matched++;
            }
        }

        if (matched == 0) {
            throw BizException.of(ErrorCode.BAD_REQUEST,
                    "官方榜单里没有匹配到我们的参赛者。请检查各参赛者在个人资料/报名时填写的平台 handle 是否与官方一致。");
        }

        auditLogService.record(operator, "CONTEST_STANDINGS_SYNC", "CONTEST", contestId,
                null, Map.of("matched", matched), "同步官方榜单：" + c.getName());
        log.info("比赛 {} 榜单同步完成：官方 {} 行，匹配 {} 个参赛者", contestId, rows.size(), matched);
        return buildVO(c, operator, true);
    }

    /** 抓到或手填的成绩落库：同一个参赛主体只保留一条，重复同步走更新 */
    private void upsertScore(Contest c, String type, Long teamId, Long userId,
                             com.hwsuanfa.algo.common.StandingsRow row) {
        LambdaQueryWrapper<ContestLog> q = new LambdaQueryWrapper<ContestLog>()
                .eq(ContestLog::getContestId, c.getId())
                .eq(ContestLog::getParticipantType, type);
        if (PART_USER.equals(type)) {
            q.eq(ContestLog::getUserId, userId);
        } else {
            q.eq(ContestLog::getTeamId, teamId);
        }
        ContestLog existing = logMapper.selectOne(q.orderByDesc(ContestLog::getId).last("LIMIT 1"));

        if (existing != null) {
            ContestLog patch = new ContestLog();
            patch.setId(existing.getId());
            patch.setAcCount(row.acCount());
            patch.setPenaltyMinutes(row.penaltyMinutes());
            patch.setDurationSeconds(row.durationSeconds());
            patch.setRankResult(row.officialRank());
            patch.setStatus(LOG_CONFIRMED);
            logMapper.updateById(patch);
            if (!LOG_CONFIRMED.equals(existing.getStatus())) {
                grantRankTo(c, PART_USER.equals(type)
                        ? List.of(userId) : teamParticipantIds(c.getId(), fromJson(existing.getMemberIds())));
            }
            return;
        }

        ContestLog logEntry = new ContestLog();
        logEntry.setContestId(c.getId());
        logEntry.setParticipantType(type);
        logEntry.setUserId(userId);
        logEntry.setTeamId(teamId);
        if (PART_TEAM.equals(type)) {
            // 队伍名单在展示时才剔除个人报名者，这里存全量
            logEntry.setMemberIds(toJson(activeMemberIds(teamId)));
        }
        logEntry.setAcCount(row.acCount());
        logEntry.setPenaltyMinutes(row.penaltyMinutes());
        logEntry.setDurationSeconds(row.durationSeconds());
        logEntry.setRankResult(row.officialRank());
        logEntry.setStatus(LOG_CONFIRMED);
        logEntry.setCreatedBy(c.getCreatedBy());
        logMapper.insert(logEntry);

        // 有成绩即有名次分（发给队伍全体或个人），幂等键保证不重复
        if (PART_USER.equals(type)) {
            grantRankTo(c, List.of(userId));
        } else {
            grantRankTo(c, teamParticipantIds(c.getId(), activeMemberIds(teamId)));
        }
    }

    private void grantRankTo(Contest c, List<Long> userIds) {
        for (Long uid : userIds) {
            scoreService.grantContestRank(uid, c.getId(),
                    "比赛「" + c.getName() + "」取得名次 " + c.getLevel() + " 级");
        }
    }

    @Override
    @Transactional
    public ContestVO submitManualScore(Long contestId, ContestLogRequest req, LoginUser me) {
        Contest c = requireContest(contestId);
        // 手动成绩只用于没有公开接口的平台
        if (standingsFetchers.stream().anyMatch(f -> f.supports(c.getPlatform()))) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "该平台可以自动抓取榜单，请使用「同步官方榜单」");
        }
        boolean isUser = PART_USER.equals(normalizeParticipantType(req.getParticipantType()));
        Long userId = isUser && req.getUserId() == null ? me.getId() : req.getUserId();
        if (isUser) {
            if (!me.isSiteOwner() && !Objects.equals(userId, me.getId())) {
                throw BizException.of(ErrorCode.FORBIDDEN, "只能填写自己的成绩");
            }
            if (userContestMapper.selectCount(new LambdaQueryWrapper<UserContest>()
                    .eq(UserContest::getContestId, contestId)
                    .eq(UserContest::getUserId, userId)
                    .eq(UserContest::getStatus, SEL_SELECTED)) == 0) {
                throw BizException.of(ErrorCode.CONTEST_NOT_SELECTED);
            }
        } else {
            if (req.getTeamId() == null) {
                throw BizException.of(ErrorCode.BAD_REQUEST, "队伍参赛必须指定队伍");
            }
            if (!me.isSiteOwner() && !isTeamLeader(req.getTeamId(), me.getId())) {
                throw BizException.of(ErrorCode.FORBIDDEN, "只有本队队长可以填写队伍成绩");
            }
            if (teamContestMapper.selectCount(new LambdaQueryWrapper<TeamContest>()
                    .eq(TeamContest::getContestId, contestId)
                    .eq(TeamContest::getTeamId, req.getTeamId())
                    .eq(TeamContest::getStatus, SEL_SELECTED)) == 0) {
                throw BizException.of(ErrorCode.CONTEST_NOT_SELECTED);
            }
        }

        com.hwsuanfa.algo.common.StandingsRow row = new com.hwsuanfa.algo.common.StandingsRow(
                "", req.getRankResult() == null ? "" : req.getRankResult(),
                req.getAcCount() == null ? 0 : req.getAcCount(),
                req.getPenaltyMinutes() == null ? 0 : req.getPenaltyMinutes(),
                req.getDurationSeconds() == null ? 0 : req.getDurationSeconds());
        upsertScore(c, isUser ? PART_USER : PART_TEAM,
                isUser ? null : req.getTeamId(), isUser ? userId : null, row);

        auditLogService.record(me, "CONTEST_MANUAL_SCORE", "CONTEST", contestId,
                null, null, "手动录入比赛成绩：" + c.getName());
        return buildVO(c, me, true);
    }

    /* ==================== 比赛记录 ==================== */

    @Override
    public List<ContestVO> records(LoginUser me, String range, String platform) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = switch (range == null ? "all" : range.toLowerCase()) {
            case "7d" -> now.minusDays(7);
            case "1m" -> now.minusMonths(1);
            case "1y" -> now.minusYears(1);
            default -> null;
        };

        // 我参加过的比赛 id = 个人报名 ∪ 本队报名
        Set<Long> ids = new LinkedHashSet<>();
        userContestMapper.selectList(new LambdaQueryWrapper<UserContest>()
                        .eq(UserContest::getUserId, me.getId())
                        .eq(UserContest::getStatus, SEL_SELECTED))
                .forEach(uc -> ids.add(uc.getContestId()));
        for (Long teamId : myTeamIdsOf(me.getId())) {
            teamContestMapper.selectList(new LambdaQueryWrapper<TeamContest>()
                            .eq(TeamContest::getTeamId, teamId)
                            .eq(TeamContest::getStatus, SEL_SELECTED))
                    .forEach(tc -> ids.add(tc.getContestId()));
        }
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Contest> q = new LambdaQueryWrapper<Contest>()
                .in(Contest::getId, ids)
                .orderByDesc(Contest::getStartAt);
        if (from != null) {
            q.ge(Contest::getStartAt, from);
        }
        if (platform != null && !platform.isBlank() && !"ALL".equalsIgnoreCase(platform)) {
            q.eq(Contest::getPlatform, platform.trim().toUpperCase());
        }

        return contestMapper.selectList(q).stream()
                .map(c -> buildVO(c, me, true)).toList();
    }

    private List<TeamContest> joinedTeams(Long contestId) {
        return teamContestMapper.selectList(new LambdaQueryWrapper<TeamContest>()
                .eq(TeamContest::getContestId, contestId)
                .eq(TeamContest::getStatus, SEL_SELECTED));
    }

    private List<UserContest> joinedIndividuals(Long contestId) {
        return userContestMapper.selectList(new LambdaQueryWrapper<UserContest>()
                .eq(UserContest::getContestId, contestId)
                .eq(UserContest::getStatus, SEL_SELECTED));
    }

    private List<Long> teamParticipantIds(Long contestId, List<Long> candidates) {
        Set<Long> individuals = joinedIndividuals(contestId).stream()
                .map(UserContest::getUserId).collect(Collectors.toSet());
        return candidates.stream().filter(id -> !individuals.contains(id)).distinct().toList();
    }

    /* ==================== 工具 ==================== */

    private Contest requireContest(Long id) {
        Contest c = contestMapper.selectById(id);
        if (c == null) {
            throw BizException.of(ErrorCode.CONTEST_NOT_FOUND);
        }
        return c;
    }

    private void apply(Contest c, ContestRequest req) {
        c.setName(req.getName().trim());
        c.setPlatform(req.getPlatform().trim().toUpperCase());
        c.setUrl(req.getUrl());
        c.setExternalId(extractExternalId(c.getPlatform(), req.getUrl()));
        String level = req.getLevel() == null ? "" : req.getLevel().trim().toUpperCase();
        if (!LEVELS.contains(level)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "比赛等级只能是 S / A / B / C / OTHER");
        }
        c.setLevel(level);
        c.setStartAt(req.getStartAt());
        c.setEndAt(req.getEndAt());
        if (c.getStartAt() != null && c.getEndAt() != null && c.getStartAt().isAfter(c.getEndAt())) {
            throw BizException.of(ErrorCode.INVALID_TIME_RANGE);
        }
    }

    /** 从比赛链接里解析平台上的比赛 id（如 CF /contest/1932 → 1932），抓榜单用 */
    private String extractExternalId(String platform, String url) {
        if (url == null || url.isBlank()) return null;
        String pattern = switch (platform.toUpperCase()) {
            case "CODEFORCES" -> "/(?:contest|gym)/(\\d+)";
            case "ATCODER" -> "/contests/([A-Za-z0-9_-]+)";
            default -> null;
        };
        if (pattern == null) return null;
        var m = java.util.regex.Pattern.compile(pattern).matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private String normalizeParticipantType(String value) {
        String type = value == null || value.isBlank() ? PART_TEAM : value.trim().toUpperCase();
        if (!PART_TEAM.equals(type) && !PART_USER.equals(type)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "参赛身份只能是 TEAM 或 USER");
        }
        return type;
    }

    private boolean isTeamLeader(Long teamId, Long userId) {
        if (teamId == null || userId == null) return false;
        Team t = teamMapper.selectById(teamId);
        return t != null && userId.equals(t.getLeaderUserId());
    }

    private boolean isTeamMember(Long teamId, Long userId) {
        return teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, "ACTIVE")) > 0;
    }

    private List<Long> activeMemberIds(Long teamId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getStatus, "ACTIVE"))
                .stream().map(TeamMember::getUserId).toList();
    }

    /** 某用户所在的所有在队队伍 id */
    private Set<Long> myTeamIdsOf(Long userId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, "ACTIVE"))
                .stream().map(TeamMember::getTeamId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String toJson(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw new IllegalStateException("序列化参赛成员失败", e);
        }
    }

    private List<Long> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("参赛成员 JSON 解析失败：{}", json);
            return Collections.emptyList();
        }
    }

    private static Long until(LocalDateTime target, LocalDateTime now) {
        return target == null ? null : Duration.between(now, target).getSeconds();
    }
}
