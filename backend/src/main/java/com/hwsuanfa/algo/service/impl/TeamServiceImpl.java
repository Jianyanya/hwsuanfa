package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.dto.AddMemberRequest;
import com.hwsuanfa.algo.dto.TeamCreateRequest;
import com.hwsuanfa.algo.dto.TeamUpdateRequest;
import com.hwsuanfa.algo.entity.Team;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.service.NotificationService;
import com.hwsuanfa.algo.service.TeamService;
import com.hwsuanfa.algo.vo.TeamBriefVO;
import com.hwsuanfa.algo.vo.TeamVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FROZEN = "FROZEN";
    private static final String MEMBER_ACTIVE = "ACTIVE";
    private static final String MEMBER_LEFT = "LEFT";

    private static final String ACTION_CREATE = "TEAM_CREATE";
    private static final String ACTION_UPDATE = "TEAM_UPDATE";
    private static final String ACTION_FREEZE = "TEAM_FREEZE";
    private static final String ACTION_UNFREEZE = "TEAM_UNFREEZE";
    private static final String ACTION_DISSOLVE = "TEAM_DISSOLVE";
    private static final String ACTION_MEMBER_ADD = "TEAM_MEMBER_ADD";
    private static final String ACTION_MEMBER_REMOVE = "TEAM_MEMBER_REMOVE";
    private static final String ACTION_LEADER_CHANGE = "TEAM_LEADER_CHANGE";

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    /** 与前端 config/constants.ts 的 TEAM_MAX_MEMBERS 保持一致 */
    @Value("${app.team.max-members:3}")
    private int defaultMaxMembers;

    /* ==================== 读 ==================== */

    @Override
    public List<TeamBriefVO> listAll() {
        List<Team> teams = teamMapper.selectList(
                new LambdaQueryWrapper<Team>().orderByAsc(Team::getId));
        if (teams.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = teams.stream().map(Team::getId).toList();
        Map<Long, Integer> counts = countActiveMembers(ids);
        Map<Long, User> leaders = loadUsers(teams.stream()
                .map(Team::getLeaderUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());

        return teams.stream().map(t -> {
            int count = counts.getOrDefault(t.getId(), 0);
            User leader = t.getLeaderUserId() == null ? null : leaders.get(t.getLeaderUserId());
            return TeamBriefVO.builder()
                    .id(t.getId())
                    .name(t.getName())
                    .description(t.getDescription())
                    .leaderUserId(t.getLeaderUserId())
                    .leaderNickname(leader == null ? null : leader.getNickname())
                    .maxMembers(t.getMaxMembers())
                    .memberCount(count)
                    .status(t.getStatus())
                    .frozenReason(t.getFrozenReason())
                    .full(count >= t.getMaxMembers())
                    .build();
        }).toList();
    }

    @Override
    public List<TeamBriefVO> myTeams(LoginUser viewer) {
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, viewer.getId())
                        .eq(TeamMember::getStatus, MEMBER_ACTIVE));
        if (memberships.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).toList();
        Map<Long, Integer> counts = countActiveMembers(teamIds);

        List<Team> teams = teamMapper.selectBatchIds(teamIds);
        Map<Long, User> leaders = loadUsers(teams.stream()
                .map(Team::getLeaderUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());

        // 保持按加入顺序（memberships 已按 id 升序）
        Map<Long, Team> byId = teams.stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        return memberships.stream()
                .map(m -> byId.get(m.getTeamId()))
                .filter(java.util.Objects::nonNull)
                .map(t -> {
                    int count = counts.getOrDefault(t.getId(), 0);
                    User leader = t.getLeaderUserId() == null ? null : leaders.get(t.getLeaderUserId());
                    return TeamBriefVO.builder()
                            .id(t.getId())
                            .name(t.getName())
                            .description(t.getDescription())
                            .leaderUserId(t.getLeaderUserId())
                            .leaderNickname(leader == null ? null : leader.getNickname())
                            .maxMembers(t.getMaxMembers())
                            .memberCount(count)
                            .status(t.getStatus())
                            .frozenReason(t.getFrozenReason())
                            .full(count >= t.getMaxMembers())
                            .build();
                })
                .toList();
    }

    @Override
    public TeamVO detail(Long teamId, LoginUser viewer) {
        Team team = requireTeam(teamId);

        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getStatus, MEMBER_ACTIVE)
                        .orderByAsc(TeamMember::getJoinedAt));

        Map<Long, User> users = loadUsers(memberships.stream().map(TeamMember::getUserId).toList());

        boolean viewerIsOwner = viewer != null && viewer.isSiteOwner();
        boolean viewerIsLeader = viewer != null && viewer.getId().equals(team.getLeaderUserId());
        boolean viewerIsMember = viewer != null && memberships.stream()
                .anyMatch(member -> viewer.getId().equals(member.getUserId()));

        List<TeamVO.MemberVO> memberVOs = new ArrayList<>();
        for (TeamMember m : memberships) {
            User u = users.get(m.getUserId());
            if (u == null) {
                continue;
            }
            memberVOs.add(TeamVO.MemberVO.builder()
                    .userId(u.getId())
                    .username(u.getUsername())
                    .nickname(u.getNickname())
                    .avatar(u.getAvatar())
                    .leader(u.getId().equals(team.getLeaderUserId()))
                    .joinedAt(m.getJoinedAt())
                    // ⚠️ 隐私：真实姓名与学号只有站主能看，这里就裁掉，
                    //    不要指望前端不渲染 —— 接口里带出去就等于泄露了
                    .realName(viewerIsOwner ? u.getRealName() : null)
                    .studentNo(viewerIsOwner ? u.getStudentNo() : null)
                    // 班级对同队成员可见，方便互相认人
                    .className(viewerIsOwner || viewerIsMember ? u.getClassName() : null)
                    .build());
        }

        return TeamVO.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .leaderUserId(team.getLeaderUserId())
                .maxMembers(team.getMaxMembers())
                .memberCount(memberVOs.size())
                .status(team.getStatus())
                .frozenReason(team.getFrozenReason())
                .createdAt(team.getCreatedAt())
                .members(memberVOs)
                .viewerIsLeader(viewerIsLeader)
                .viewerIsSiteOwner(viewerIsOwner)
                // 本阶段队伍结构类操作仅站主可做（计划书：成员增减与队长变更归站主）
                .editable(viewerIsOwner)
                .build();
    }

    /* ==================== 站主写操作 ==================== */

    @Override
    @Transactional
    public TeamVO create(TeamCreateRequest req, LoginUser operator) {
        String name = req.getName().trim();
        if (existsName(name, null)) {
            throw BizException.of(ErrorCode.TEAM_NAME_EXISTS);
        }

        Long leaderId = req.getLeaderUserId();
        if (leaderId != null) {
            requireUser(leaderId);
        }

        Team team = new Team();
        team.setName(name);
        team.setDescription(req.getDescription());
        team.setMaxMembers(defaultMaxMembers);
        team.setStatus(STATUS_ACTIVE);
        teamMapper.insert(team);

        // 建队时带了队长：必须同时把他加成成员，否则会出现「队长不是成员」的非法状态
        if (leaderId != null) {
            insertMembership(team.getId(), leaderId);
            Team patch = new Team();
            patch.setId(team.getId());
            patch.setLeaderUserId(leaderId);
            teamMapper.updateById(patch);
        }

        auditLogService.record(operator, ACTION_CREATE, "TEAM", team.getId(),
                null, snapshot(team), "创建队伍：" + name);

        log.info("站主 {} 创建队伍 {}（id={}）", operator.getNickname(), name, team.getId());
        return detail(team.getId(), operator);
    }

    @Override
    @Transactional
    public TeamVO update(Long teamId, TeamUpdateRequest req, LoginUser operator) {
        Team team = requireTeam(teamId);
        Map<String, Object> before = snapshot(team);

        Team patch = new Team();
        patch.setId(teamId);

        if (req.getName() != null && !req.getName().isBlank()) {
            String name = req.getName().trim();
            if (existsName(name, teamId)) {
                throw BizException.of(ErrorCode.TEAM_NAME_EXISTS);
            }
            patch.setName(name);
        }
        if (req.getDescription() != null) {
            patch.setDescription(req.getDescription());
        }

        teamMapper.updateById(patch);

        Team after = requireTeam(teamId);
        auditLogService.record(operator, ACTION_UPDATE, "TEAM", teamId,
                before, snapshot(after), "编辑队伍信息");

        return detail(teamId, operator);
    }

    @Override
    @Transactional
    public TeamVO addMember(Long teamId, AddMemberRequest req, LoginUser operator) {
        // ⚠️ 先锁队伍行再数成员：两个并发请求会串行化，避免同时通过「未满员」检查而超员
        Team team = teamMapper.selectByIdForUpdate(teamId);
        if (team == null) {
            throw BizException.of(ErrorCode.TEAM_NOT_FOUND);
        }
        if (STATUS_FROZEN.equals(team.getStatus())) {
            throw BizException.of(ErrorCode.TEAM_FROZEN);
        }

        Long userId = req.getUserId();
        User user = requireUser(userId);

        // 已是有效成员则拒绝；已退出（LEFT）的允许重新加入，复用同一条记录
        TeamMember existing = findMembership(teamId, userId);
        if (existing != null && MEMBER_ACTIVE.equals(existing.getStatus())) {
            throw BizException.of(ErrorCode.ALREADY_TEAM_MEMBER);
        }

        long activeCount = countActive(teamId);
        if (activeCount >= team.getMaxMembers()) {
            throw BizException.of(ErrorCode.MEMBER_LIMIT_EXCEEDED,
                    "队伍成员已达上限（" + team.getMaxMembers() + " 人）");
        }

        if (existing != null) {
            TeamMember rejoin = new TeamMember();
            rejoin.setId(existing.getId());
            rejoin.setStatus(MEMBER_ACTIVE);
            rejoin.setJoinedAt(LocalDateTime.now());
            rejoin.setLeftAt(null);
            teamMemberMapper.updateById(rejoin);
        } else {
            insertMembership(teamId, userId);
        }

        boolean shouldBeLeader = Boolean.TRUE.equals(req.getAsLeader())
                || team.getLeaderUserId() == null;

        if (shouldBeLeader) {
            Team patch = new Team();
            patch.setId(teamId);
            patch.setLeaderUserId(userId);
            teamMapper.updateById(patch);
        }

        String detail = "添加成员：" + user.getNickname()
                + (shouldBeLeader ? "（同时指定为队长）" : "");
        auditLogService.record(operator, ACTION_MEMBER_ADD, "TEAM", teamId,
                null, Map.of("userId", userId, "asLeader", shouldBeLeader), detail);
        notificationService.send(userId, "TEAM_MEMBER_ADDED", "你加入了队伍",
                "你已加入队伍「" + team.getName() + "」。", "TEAM", teamId);

        if (shouldBeLeader) {
            auditLogService.record(operator, ACTION_LEADER_CHANGE, "TEAM", teamId,
                    Map.of("leaderUserId", team.getLeaderUserId() == null ? "" : team.getLeaderUserId()),
                    Map.of("leaderUserId", userId),
                    "队长变更为：" + user.getNickname());
        }

        return detail(teamId, operator);
    }

    @Override
    @Transactional
    public TeamVO removeMember(Long teamId, Long userId, Long newLeaderId, LoginUser operator) {
        Team team = teamMapper.selectByIdForUpdate(teamId);
        if (team == null) {
            throw BizException.of(ErrorCode.TEAM_NOT_FOUND);
        }
        if (STATUS_FROZEN.equals(team.getStatus())) {
            throw BizException.of(ErrorCode.TEAM_FROZEN);
        }

        TeamMember member = findMembership(teamId, userId);
        if (member == null || !MEMBER_ACTIVE.equals(member.getStatus())) {
            throw BizException.of(ErrorCode.NOT_TEAM_MEMBER);
        }
        User user = requireUser(userId);

        long activeCount = countActive(teamId);
        boolean isLeader = userId.equals(team.getLeaderUserId());

        if (isLeader) {
            // 边界：队伍只剩队长一人 —— 不能把他移除，否则队伍就没有队长也没有成员了
            if (activeCount <= 1) {
                throw BizException.of(ErrorCode.CANNOT_REMOVE_LAST_LEADER);
            }
            // 还有别人：必须先明确指定新队长，不能让队伍出现「无队长」的空窗
            if (newLeaderId == null) {
                throw BizException.of(ErrorCode.REMOVE_LEADER_NEEDS_TRANSFER);
            }
            transferLeader(teamId, userId, newLeaderId, operator);
        }

        TeamMember patch = new TeamMember();
        patch.setId(member.getId());
        patch.setStatus(MEMBER_LEFT);
        patch.setLeftAt(LocalDateTime.now());
        teamMemberMapper.updateById(patch);

        auditLogService.record(operator, ACTION_MEMBER_REMOVE, "TEAM", teamId,
                Map.of("userId", userId, "nickname", user.getNickname()),
                Map.of("status", MEMBER_LEFT),
                "移除成员：" + user.getNickname());
        notificationService.send(userId, "TEAM_MEMBER_REMOVED", "你已退出队伍",
                "你已被移出队伍「" + team.getName() + "」。", "TEAM", teamId);

        log.info("站主 {} 从队伍 {} 移除成员 {}", operator.getNickname(), teamId, user.getNickname());
        return detail(teamId, operator);
    }

    @Override
    @Transactional
    public TeamVO changeLeader(Long teamId, Long newLeaderId, LoginUser operator) {
        Team team = teamMapper.selectByIdForUpdate(teamId);
        if (team == null) {
            throw BizException.of(ErrorCode.TEAM_NOT_FOUND);
        }

        // 换队长不改成员构成，所以冻结状态下仍允许 —— 站主需要能修正被冻结队伍的管理问题
        if (newLeaderId.equals(team.getLeaderUserId())) {
            return detail(teamId, operator); // 幂等：已是队长则直接返回
        }

        transferLeader(teamId, team.getLeaderUserId(), newLeaderId, operator);
        return detail(teamId, operator);
    }

    @Override
    @Transactional
    public void freeze(Long teamId, String reason, LoginUser operator) {
        Team team = requireTeam(teamId);
        if (STATUS_FROZEN.equals(team.getStatus())) {
            return;
        }

        Team patch = new Team();
        patch.setId(teamId);
        patch.setStatus(STATUS_FROZEN);
        patch.setFrozenReason(reason);
        teamMapper.updateById(patch);

        auditLogService.record(operator, ACTION_FREEZE, "TEAM", teamId,
                Map.of("status", STATUS_ACTIVE), Map.of("status", STATUS_FROZEN),
                "冻结队伍" + (reason == null || reason.isBlank() ? "" : "，原因：" + reason));
    }

    @Override
    @Transactional
    public void unfreeze(Long teamId, LoginUser operator) {
        Team team = requireTeam(teamId);
        if (STATUS_ACTIVE.equals(team.getStatus())) {
            return;
        }

        teamMapper.update(null, new LambdaUpdateWrapper<Team>()
                .eq(Team::getId, teamId)
                .set(Team::getStatus, STATUS_ACTIVE)
                .set(Team::getFrozenReason, null));

        auditLogService.record(operator, ACTION_UNFREEZE, "TEAM", teamId,
                Map.of("status", STATUS_FROZEN), Map.of("status", STATUS_ACTIVE), "解冻队伍");
    }

    @Override
    @Transactional
    public void dissolve(Long teamId, LoginUser operator) {
        Team team = requireTeam(teamId);

        // 计划书：站主可以冻结队伍，但不直接删除历史任务和积分记录。
        // 有业务痕迹就拒绝硬删除，引导改用冻结。
        int history = teamMapper.countAssignmentTargets(teamId)
                + teamMapper.countAssignmentProgress(teamId)
                + teamMapper.countContestLogs(teamId)
                + teamMapper.countSelectedContests(teamId)
                + teamMapper.countProblemLists(teamId)
                + teamMapper.countAnnouncements(teamId);

        if (history > 0) {
            throw BizException.of(ErrorCode.TEAM_HAS_SCORE_HISTORY,
                    "该队伍存在 " + history + " 条关联记录（作业/比赛/题单/公告），不能删除。请改为冻结队伍。");
        }

        auditLogService.record(operator, ACTION_DISSOLVE, "TEAM", teamId,
                snapshot(team), null, "解散队伍：" + team.getName());

        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        teamMapper.deleteById(teamId);

        log.info("站主 {} 解散队伍 {}（id={}）", operator.getNickname(), team.getName(), teamId);
    }

    /* ==================== 内部工具 ==================== */

    /** 变更队长。调用方必须已持有队伍行锁，并保证 newLeaderId 是本队 ACTIVE 成员 */
    private void transferLeader(Long teamId, Long oldLeaderId, Long newLeaderId, LoginUser operator) {
        if (newLeaderId == null) {
            throw BizException.of(ErrorCode.LEADER_REQUIRED);
        }
        if (newLeaderId.equals(oldLeaderId)) {
            return;
        }

        TeamMember target = findMembership(teamId, newLeaderId);
        if (target == null || !MEMBER_ACTIVE.equals(target.getStatus())) {
            // 队长必须同时是成员 —— 这是硬约束，不能把外人设成队长
            throw BizException.of(ErrorCode.LEADER_MUST_BE_OTHER_MEMBER);
        }

        User newLeader = requireUser(newLeaderId);

        Team patch = new Team();
        patch.setId(teamId);
        patch.setLeaderUserId(newLeaderId);
        teamMapper.updateById(patch);

        auditLogService.record(operator, ACTION_LEADER_CHANGE, "TEAM", teamId,
                Map.of("leaderUserId", oldLeaderId == null ? "" : oldLeaderId),
                Map.of("leaderUserId", newLeaderId),
                "队长变更为：" + newLeader.getNickname());
    }

    private void insertMembership(Long teamId, Long userId) {
        TeamMember m = new TeamMember();
        m.setTeamId(teamId);
        m.setUserId(userId);
        m.setStatus(MEMBER_ACTIVE);
        m.setJoinedAt(LocalDateTime.now());
        teamMemberMapper.insert(m);
    }

    private TeamMember findMembership(Long teamId, Long userId) {
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
    }

    private long countActive(Long teamId) {
        return teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getStatus, MEMBER_ACTIVE));
    }

    /** 批量统计活跃成员数，避免列表页 N+1 */
    private Map<Long, Integer> countActiveMembers(List<Long> teamIds) {
        if (teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<TeamMember> all = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .in(TeamMember::getTeamId, teamIds)
                .eq(TeamMember::getStatus, MEMBER_ACTIVE));
        Map<Long, Integer> map = new HashMap<>();
        for (TeamMember m : all) {
            map.merge(m.getTeamId(), 1, Integer::sum);
        }
        return map;
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Team requireTeam(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw BizException.of(ErrorCode.TEAM_NOT_FOUND);
        }
        return team;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private boolean existsName(String name, Long excludeId) {
        LambdaQueryWrapper<Team> q = new LambdaQueryWrapper<Team>().eq(Team::getName, name);
        if (excludeId != null) {
            q.ne(Team::getId, excludeId);
        }
        return teamMapper.selectCount(q) > 0;
    }

    /**
     * 审计用快照。
     *
     * ⚠️ 必须返回对象（由 Jackson 序列化成 JSON），**不能返回手写的 "k=v; k=v" 字符串** ——
     * audit_log.before_json / after_json 是 MySQL 的 JSON 列，非 JSON 文本会被拒绝并报
     * Data truncation: Invalid JSON text。
     * 用 LinkedHashMap 而不是 Map.of：Map.of 不接受 null 值，而 leaderUserId 可能为 null。
     */
    private Map<String, Object> snapshot(Team t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("status", t.getStatus());
        m.put("leaderUserId", t.getLeaderUserId());
        m.put("maxMembers", t.getMaxMembers());
        return m;
    }
}
