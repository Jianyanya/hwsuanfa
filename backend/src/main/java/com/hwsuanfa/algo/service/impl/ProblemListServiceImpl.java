package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.common.ProblemRefParser;
import com.hwsuanfa.algo.dto.ProblemImportRequest;
import com.hwsuanfa.algo.dto.ProblemListRequest;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.entity.ProblemList;
import com.hwsuanfa.algo.entity.ProblemListItem;
import com.hwsuanfa.algo.entity.Team;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.ProblemListItemMapper;
import com.hwsuanfa.algo.mapper.ProblemListMapper;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.service.DifficultyService;
import com.hwsuanfa.algo.service.ProblemListService;
import com.hwsuanfa.algo.service.ProblemService;
import com.hwsuanfa.algo.vo.ProblemListVO;
import com.hwsuanfa.algo.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
public class ProblemListServiceImpl implements ProblemListService {

    private static final String SCOPE_PERSONAL = "PERSONAL";
    private static final String SCOPE_TEAM = "TEAM";

    private static final String AUDIT_APPROVED = "APPROVED";
    private static final String AUDIT_PENDING = "PENDING";
    private static final String AUDIT_REJECTED = "REJECTED";

    private static final BigDecimal FALLBACK_FIRST_AC = new BigDecimal("2");

    private final ProblemListMapper listMapper;
    private final ProblemListItemMapper itemMapper;
    private final ProblemMapper problemMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ProblemService problemService;
    private final DifficultyService difficultyService;
    private final AuditLogService auditLogService;

    /* ==================== 读 ==================== */

    @Override
    public List<ProblemListVO> myLists(LoginUser me) {
        List<Long> myTeams = userTeamIds(me.getId());

        LambdaQueryWrapper<ProblemList> q = new LambdaQueryWrapper<>();
        if (me.isSiteOwner()) {
            // 站主看全部，方便统一维护
        } else {
            Set<Long> scopeIds = new LinkedHashSet<>(myTeams);
            if (scopeIds.isEmpty()) {
                q.eq(ProblemList::getOwnerId, me.getId());
            } else {
                q.and(w -> w.eq(ProblemList::getOwnerId, me.getId())
                        .or(o -> o.eq(ProblemList::getScope, SCOPE_TEAM).in(ProblemList::getTeamId, scopeIds)));
            }
        }
        q.orderByDesc(ProblemList::getUpdatedAt).orderByDesc(ProblemList::getId);

        List<ProblemList> lists = listMapper.selectList(q);
        if (!me.isSiteOwner()) {
            lists = lists.stream().filter(list -> canSee(list, me, myTeams)).toList();
        }
        return withCounts(lists, me, false);
    }

    @Override
    public ProblemListVO detail(Long id, LoginUser me) {
        ProblemList list = requireList(id);
        requireVisible(list, me);
        return withCounts(List.of(list), me, true).get(0);
    }

    @Override
    public List<ProblemListVO> adminList(String scope) {
        LambdaQueryWrapper<ProblemList> q = new LambdaQueryWrapper<>();
        if (scope != null && !scope.isBlank() && !"ALL".equalsIgnoreCase(scope)) {
            q.eq(ProblemList::getScope, scope.trim().toUpperCase());
        }
        q.orderByDesc(ProblemList::getUpdatedAt).orderByDesc(ProblemList::getId);
        return withCounts(listMapper.selectList(q), null, false);
    }

    /* ==================== 写 ==================== */

    @Override
    @Transactional
    public ProblemListVO create(ProblemListRequest req, LoginUser me) {
        String scope = normalizeScope(req.getScope());
        Long teamId = null;

        if (SCOPE_TEAM.equals(scope)) {
            teamId = req.getTeamId();
            if (teamId == null) {
                throw BizException.of(ErrorCode.BAD_REQUEST, "队伍题单必须指定队伍");
            }
            requireTeam(teamId);
            if (!me.isSiteOwner() && !userTeamIds(me.getId()).contains(teamId)) {
                throw BizException.of(ErrorCode.FORBIDDEN, "只能向自己的队伍提交题单");
            }
        }

        ProblemList list = new ProblemList();
        list.setName(req.getName().trim());
        list.setDescription(req.getDescription());
        list.setScope(scope);
        list.setTeamId(teamId);
        list.setOwnerId(me.getId());
        list.setLastEditorId(me.getId());

        // 队员建队伍题单 → 进待审核，由队长或站主确认后才算正式内容
        boolean isCoach = me.isSiteOwner();
        boolean isLeader = SCOPE_TEAM.equals(scope) && isTeamLeader(teamId, me.getId());
        list.setAuditStatus(isCoach || isLeader || SCOPE_PERSONAL.equals(scope)
                ? AUDIT_APPROVED : AUDIT_PENDING);

        listMapper.insert(list);

        // 创建时可以直接带题目
        List<Long> ids = new ArrayList<>();
        if (req.getProblemIds() != null) {
            ids.addAll(req.getProblemIds());
        }
        if (req.getPasteText() != null && !req.getPasteText().isBlank()) {
            ids.addAll(resolvePastedToIds(req.getPasteText(), req.getDefaultPlatform(), me));
        }
        if (!ids.isEmpty()) {
            addItems(list.getId(), new LinkedHashSet<>(ids));
        }

        auditLogService.record(me, "PROBLEM_LIST_CREATE", "PROBLEM_LIST", list.getId(),
                null, Map.of("scope", scope, "problems", ids.size()),
                "创建题单：" + list.getName());

        return detail(list.getId(), me);
    }

    @Override
    @Transactional
    public ProblemListVO update(Long id, ProblemListRequest req, LoginUser me) {
        ProblemList list = requireListForUpdate(id);
        requireEditable(list, me);

        ProblemList patch = new ProblemList();
        patch.setId(id);
        patch.setName(req.getName().trim());
        patch.setDescription(req.getDescription());
        patch.setLastEditorId(me.getId());
        listMapper.updateById(patch);
        touchAfterEdit(list, me);

        auditLogService.record(me, "PROBLEM_LIST_UPDATE", "PROBLEM_LIST", id,
                null, null, "编辑题单：" + patch.getName());
        return detail(id, me);
    }

    @Override
    @Transactional
    public void delete(Long id, LoginUser me) {
        ProblemList list = requireListForUpdate(id);
        requireEditable(list, me);

        auditLogService.record(me, "PROBLEM_LIST_DELETE", "PROBLEM_LIST", id,
                Map.of("name", list.getName()), null, "删除题单：" + list.getName());

        // problem_list_item 有 ON DELETE CASCADE
        listMapper.deleteById(id);
    }

    /* ==================== 题目 ==================== */

    @Override
    @Transactional
    public ProblemListVO importProblems(Long id, ProblemImportRequest req, LoginUser me) {
        ProblemList list = requireListForUpdate(id);
        requireEditable(list, me);

        Set<Long> ids = new LinkedHashSet<>();
        if (req.getProblemIds() != null) {
            ids.addAll(req.getProblemIds());
        }
        if (req.getPasteText() != null && !req.getPasteText().isBlank()) {
            ids.addAll(resolvePastedToIds(req.getPasteText(), req.getDefaultPlatform(), me));
        }
        if (ids.isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "没有可导入的题目");
        }

        int added = addItems(id, ids);

        touchAfterEdit(list, me);

        auditLogService.record(me, "PROBLEM_LIST_IMPORT", "PROBLEM_LIST", id,
                null, Map.of("requested", ids.size(), "added", added, "skipped", ids.size() - added),
                "导入题目 " + added + " 道");

        return detail(id, me);
    }

    @Override
    public ProblemRefParser.Result previewImport(String text, String defaultPlatform) {
        return ProblemRefParser.parse(text, defaultPlatform);
    }

    @Override
    @Transactional
    public ProblemListVO removeProblem(Long id, Long problemId, LoginUser me) {
        ProblemList list = requireListForUpdate(id);
        requireEditable(list, me);
        itemMapper.delete(new LambdaQueryWrapper<ProblemListItem>()
                .eq(ProblemListItem::getProblemListId, id)
                .eq(ProblemListItem::getProblemId, problemId));
        touchAfterEdit(list, me);
        return detail(id, me);
    }

    @Override
    @Transactional
    public ProblemListVO reorder(Long id, List<Long> problemIds, LoginUser me) {
        ProblemList list = requireListForUpdate(id);
        requireEditable(list, me);

        List<ProblemListItem> items = itemMapper.selectList(new LambdaQueryWrapper<ProblemListItem>()
                .eq(ProblemListItem::getProblemListId, id));

        Set<Long> existing = items.stream().map(ProblemListItem::getProblemId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> target = new LinkedHashSet<>(problemIds == null ? List.of() : problemIds);
        if (!existing.equals(target)) {
            // 只允许「同一批题目换顺序」，避免把重排接口当成删除接口用
            throw BizException.of(ErrorCode.BAD_REQUEST,
                    "重排必须包含题单里的全部题目（共 " + existing.size() + " 道）");
        }

        Map<Long, ProblemListItem> byProblem = items.stream()
                .collect(Collectors.toMap(ProblemListItem::getProblemId, Function.identity()));
        int order = 0;
        for (Long pid : target) {
            ProblemListItem item = byProblem.get(pid);
            ProblemListItem patch = new ProblemListItem();
            patch.setId(item.getId());
            patch.setSortOrder(order++);
            itemMapper.updateById(patch);
        }
        touchAfterEdit(list, me);
        return detail(id, me);
    }

    /* ==================== 审核 ==================== */

    @Override
    @Transactional
    public ProblemListVO audit(Long id, boolean approve, String rejectReason, LoginUser me) {
        ProblemList list = requireListForUpdate(id);

        // 审核只有站主和该队队长能做；本人不能审自己的提交
        boolean allowed = me.isSiteOwner()
                || (SCOPE_TEAM.equals(list.getScope()) && isTeamLeader(list.getTeamId(), me.getId()));
        if (!allowed) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
        if (Objects.equals(list.getOwnerId(), me.getId()) && !me.isSiteOwner()) {
            throw BizException.of(ErrorCode.FORBIDDEN, "不能审核自己提交的题单");
        }

        ProblemList patch = new ProblemList();
        patch.setId(id);
        patch.setAuditStatus(approve ? AUDIT_APPROVED : AUDIT_REJECTED);
        patch.setAuditedBy(me.getId());
        patch.setAuditedAt(java.time.LocalDateTime.now());
        patch.setRejectReason(approve ? null : rejectReason);
        listMapper.updateById(patch);
        if (approve) {
            listMapper.update(null, new LambdaUpdateWrapper<ProblemList>()
                    .eq(ProblemList::getId, id).set(ProblemList::getRejectReason, null));
        }

        auditLogService.record(me, approve ? "PROBLEM_LIST_APPROVE" : "PROBLEM_LIST_REJECT",
                "PROBLEM_LIST", id, null, Map.of("approve", approve),
                (approve ? "通过题单：" : "驳回题单：") + list.getName());

        return detail(id, me);
    }

    /* ==================== 作业联动 ==================== */

    @Override
    public List<Long> problemIdsOf(Long listId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ProblemListItem>()
                        .eq(ProblemListItem::getProblemListId, listId)
                        .orderByAsc(ProblemListItem::getSortOrder))
                .stream().map(ProblemListItem::getProblemId).toList();
    }

    /* ==================== 内部 ==================== */

    /** 把粘贴文本解析成题目 id：已存在的直接复用，不存在的建占位题目 */
    private List<Long> resolvePastedToIds(String text, String defaultPlatform, LoginUser me) {
        ProblemRefParser.Result parsed = ProblemRefParser.parse(text, defaultPlatform);
        if (parsed.problems().isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST,
                    "没能从粘贴内容里识别出任何题号"
                            + (parsed.unparsed().isEmpty() ? "" : "，例如这一行：「" + parsed.unparsed().get(0) + "」"));
        }
        if (!parsed.unparsed().isEmpty()) {
            log.info("批量导入有 {} 行未能识别（已跳过）：{}",
                    parsed.unparsed().size(), parsed.unparsed());
        }
        List<Long> ids = new ArrayList<>();
        for (ProblemRefParser.ParsedProblem p : parsed.problems()) {
            Problem problem = problemService.findOrCreate(
                    p.platform(), p.problemNo(), p.url(), me.getId());
            if (problem != null) {
                ids.add(problem.getId());
            }
        }
        return ids;
    }

    /** 加入题目，跳过已在单里的；返回真正新增的数量 */
    private int addItems(Long listId, Set<Long> problemIds) {
        if (problemIds.isEmpty()) {
            return 0;
        }
        if (problemMapper.selectCount(new LambdaQueryWrapper<Problem>()
                .in(Problem::getId, problemIds)) != problemIds.size()) {
            throw BizException.of(ErrorCode.PROBLEM_NOT_FOUND);
        }
        List<ProblemListItem> items = itemMapper.selectList(new LambdaQueryWrapper<ProblemListItem>()
                .eq(ProblemListItem::getProblemListId, listId));
        Set<Long> existing = items.stream().map(ProblemListItem::getProblemId).collect(Collectors.toSet());

        int order = items.stream().map(ProblemListItem::getSortOrder).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(-1) + 1;
        int added = 0;
        for (Long pid : problemIds) {
            if (existing.contains(pid)) {
                continue;
            }
            ProblemListItem item = new ProblemListItem();
            item.setProblemListId(listId);
            item.setProblemId(pid);
            item.setSortOrder(order++);
            itemMapper.insert(item);
            added++;
        }
        return added;
    }

    /** 组装 VO；withProblems 为 true 时带题目明细 */
    private List<ProblemListVO> withCounts(List<ProblemList> lists, LoginUser viewer, boolean withProblems) {
        if (lists.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> listIds = lists.stream().map(ProblemList::getId).toList();

        Map<Long, List<ProblemListItem>> itemsByList = itemMapper.selectList(
                        new LambdaQueryWrapper<ProblemListItem>()
                                .in(ProblemListItem::getProblemListId, listIds)
                                .orderByAsc(ProblemListItem::getSortOrder))
                .stream().collect(Collectors.groupingBy(ProblemListItem::getProblemListId));

        Map<Long, Team> teams = loadTeams(lists);
        Map<Long, User> users = loadUsers(lists);

        List<ProblemVO> allProblems = withProblems ? loadProblemVOs(itemsByList) : List.of();
        Map<Long, ProblemVO> problemById = allProblems.stream()
                .collect(Collectors.toMap(ProblemVO::getId, Function.identity(), (a, b) -> a));

        return lists.stream().map(l -> {
            List<ProblemListItem> items = itemsByList.getOrDefault(l.getId(), List.of());
            Team team = l.getTeamId() == null ? null : teams.get(l.getTeamId());
            User owner = users.get(l.getOwnerId());

            List<ProblemVO> detail = withProblems
                    ? items.stream().map(i -> problemById.get(i.getProblemId()))
                            .filter(Objects::nonNull).toList()
                    : null;

            return ProblemListVO.builder()
                    .id(l.getId())
                    .name(l.getName())
                    .description(l.getDescription())
                    .scope(l.getScope())
                    .teamId(l.getTeamId())
                    .teamName(team == null ? null : team.getName())
                    .ownerId(l.getOwnerId())
                    .ownerNickname(owner == null ? null : owner.getNickname())
                    .lastEditorId(l.getLastEditorId())
                    .auditStatus(l.getAuditStatus())
                    .rejectReason(l.getRejectReason())
                    .problemCount(items.size())
                    .problems(detail)
                    .editable(viewer != null && canEdit(l, viewer))
                    .createdAt(l.getCreatedAt())
                    .updatedAt(l.getUpdatedAt())
                    .build();
        }).toList();
    }

    private List<ProblemVO> loadProblemVOs(Map<Long, List<ProblemListItem>> itemsByList) {
        Set<Long> ids = itemsByList.values().stream()
                .flatMap(List::stream).map(ProblemListItem::getProblemId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Problem> problems = problemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        return ids.stream().map(problems::get).filter(Objects::nonNull)
                .map(p -> ProblemVO.builder()
                        .id(p.getId())
                        .title(p.getTitle())
                        .platform(p.getPlatform())
                        .problemNo(p.getProblemNo())
                        .url(p.getUrl())
                        .difficulty(p.getDifficulty())
                        .tags(p.getTags())
                        .score(difficultyService.resolveScore(
                                p.getPlatform(), p.getDifficulty(), FALLBACK_FIRST_AC))
                        .build())
                .toList();
    }

    private Map<Long, Team> loadTeams(List<ProblemList> lists) {
        List<Long> ids = lists.stream().map(ProblemList::getTeamId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return teamMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    private Map<Long, User> loadUsers(List<ProblemList> lists) {
        List<Long> ids = lists.stream().map(ProblemList::getOwnerId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 能否编辑：站主全权；个人题单看本人；队伍题单看是否本队队长 */
    private boolean canEdit(ProblemList list, LoginUser me) {
        if (me.isSiteOwner()) {
            return true;
        }
        if (SCOPE_PERSONAL.equals(list.getScope())) {
            return Objects.equals(list.getOwnerId(), me.getId());
        }
        if (isTeamLeader(list.getTeamId(), me.getId())) {
            return true;
        }
        return Objects.equals(list.getOwnerId(), me.getId())
                && (AUDIT_PENDING.equals(list.getAuditStatus()) || AUDIT_REJECTED.equals(list.getAuditStatus()))
                && userTeamIds(me.getId()).contains(list.getTeamId());
    }

    private void touchAfterEdit(ProblemList list, LoginUser me) {
        boolean resubmit = SCOPE_TEAM.equals(list.getScope())
                && !me.isSiteOwner() && !isTeamLeader(list.getTeamId(), me.getId())
                && Objects.equals(list.getOwnerId(), me.getId());
        if (resubmit) {
            listMapper.update(null, new LambdaUpdateWrapper<ProblemList>()
                    .eq(ProblemList::getId, list.getId())
                    .set(ProblemList::getLastEditorId, me.getId())
                    .set(ProblemList::getAuditStatus, AUDIT_PENDING)
                    .set(ProblemList::getAuditedBy, null)
                    .set(ProblemList::getAuditedAt, null)
                    .set(ProblemList::getRejectReason, null));
        } else {
            ProblemList patch = new ProblemList();
            patch.setId(list.getId());
            patch.setLastEditorId(me.getId());
            listMapper.updateById(patch);
        }
    }

    private void requireEditable(ProblemList list, LoginUser me) {
        if (!canEdit(list, me)) {
            // 队员对队伍题单是「可提交、不可直接改」，需要队长或站主确认
            throw BizException.of(ErrorCode.FORBIDDEN,
                    SCOPE_TEAM.equals(list.getScope())
                            ? "只能修订自己待审核或被驳回的队伍题单；已通过的题单由队长或站主管理"
                            : "只能修改自己的个人题单");
        }
    }

    private void requireVisible(ProblemList list, LoginUser me) {
        if (!canSee(list, me, userTeamIds(me.getId()))) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canSee(ProblemList list, LoginUser me, List<Long> myTeams) {
        if (me.isSiteOwner()) {
            return true;
        }
        if (SCOPE_PERSONAL.equals(list.getScope())) {
            return Objects.equals(list.getOwnerId(), me.getId());
        }
        if (!myTeams.contains(list.getTeamId())) {
            return false;
        }
        return AUDIT_APPROVED.equals(list.getAuditStatus())
                || Objects.equals(list.getOwnerId(), me.getId())
                || isTeamLeader(list.getTeamId(), me.getId());
    }

    private boolean isTeamLeader(Long teamId, Long userId) {
        if (teamId == null || userId == null) {
            return false;
        }
        Team team = teamMapper.selectById(teamId);
        return team != null && userId.equals(team.getLeaderUserId());
    }

    private List<Long> userTeamIds(Long userId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, "ACTIVE"))
                .stream().map(TeamMember::getTeamId).toList();
    }

    private ProblemList requireList(Long id) {
        ProblemList list = listMapper.selectById(id);
        if (list == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "题单不存在");
        }
        return list;
    }

    private ProblemList requireListForUpdate(Long id) {
        ProblemList list = listMapper.selectByIdForUpdate(id);
        if (list == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "题单不存在");
        }
        return list;
    }

    private void requireTeam(Long teamId) {
        if (teamMapper.selectById(teamId) == null) {
            throw BizException.of(ErrorCode.TEAM_NOT_FOUND);
        }
    }

    private static String normalizeScope(String scope) {
        String s = scope == null ? "" : scope.trim().toUpperCase();
        if (!SCOPE_PERSONAL.equals(s) && !SCOPE_TEAM.equals(s)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "题单归属只能是 PERSONAL 或 TEAM");
        }
        return s;
    }
}
