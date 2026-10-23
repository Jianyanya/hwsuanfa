package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.AssignmentStatus;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.dto.AssignmentRequest;
import com.hwsuanfa.algo.entity.Assignment;
import com.hwsuanfa.algo.entity.AssignmentProblem;
import com.hwsuanfa.algo.entity.AssignmentProgress;
import com.hwsuanfa.algo.entity.AssignmentTarget;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.entity.ScoreEvent;
import com.hwsuanfa.algo.entity.SubmissionRecord;
import com.hwsuanfa.algo.entity.Team;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.AssignmentMapper;
import com.hwsuanfa.algo.mapper.AssignmentProblemMapper;
import com.hwsuanfa.algo.mapper.AssignmentProgressMapper;
import com.hwsuanfa.algo.mapper.AssignmentTargetMapper;
import com.hwsuanfa.algo.common.ProblemRefParser;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.mapper.ScoreEventMapper;
import com.hwsuanfa.algo.mapper.SubmissionRecordMapper;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AssignmentService;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.service.DifficultyService;
import com.hwsuanfa.algo.service.ProblemListService;
import com.hwsuanfa.algo.service.ScoreService;
import com.hwsuanfa.algo.vo.AssignmentVO;
import com.hwsuanfa.algo.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private static final String TYPE_TEAM = "TEAM";
    private static final String TYPE_GLOBAL = "GLOBAL";
    private static final String TYPE_DAILY = "DAILY";

    private static final String PROG_IN_PROGRESS = "IN_PROGRESS";
    private static final String PROG_COMPLETED = "COMPLETED";

    /** 规则缺失时的兜底分值（难度分档与 score_rule 都查不到才会用到） */
    private static final BigDecimal FALLBACK_FIRST_AC = new BigDecimal("2");
    private static final BigDecimal FALLBACK_DAILY = new BigDecimal("5");
    private static final BigDecimal FALLBACK_ASSIGNMENT = new BigDecimal("10");

    private final AssignmentMapper assignmentMapper;
    private final AssignmentTargetMapper targetMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final AssignmentProgressMapper progressMapper;
    private final SubmissionRecordMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final com.hwsuanfa.algo.service.ProblemService problemService;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ScoreEventMapper scoreEventMapper;
    private final ScoreService scoreService;
    private final DifficultyService difficultyService;
    private final ProblemListService problemListService;
    private final AuditLogService auditLogService;
    private final UserMapper userMapper;

    /* ==================== 站主 ==================== */

    @Override
    @Transactional
    public AssignmentVO create(AssignmentRequest req, LoginUser operator) {
        String type = normalizeType(req.getType());

        // 截止必须严格晚于开始
        if (!req.getDueAt().isAfter(req.getStartAt())) {
            throw BizException.of(ErrorCode.INVALID_TIME_RANGE);
        }
        List<Long> problemIds = resolveProblemIds(req);
        if (problemIds.isEmpty()) {
            throw BizException.of(ErrorCode.ASSIGNMENT_NEEDS_PROBLEM);
        }
        requireProblemsExist(problemIds);

        // ---- 目标队伍 ----
        List<Long> teamIds;
        if (TYPE_GLOBAL.equals(type)) {
            // ⚠️ 全体作业：在**发布这一刻**取全部有效队伍做成快照。
            //    以后新增的队伍不会被追溯进来，历史作业范围也不会变。
            teamIds = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                            .eq(Team::getStatus, "ACTIVE"))
                    .stream().map(Team::getId).toList();
        } else {
            teamIds = distinct(req.getTeamIds() == null ? List.of() : req.getTeamIds());
            if (TYPE_TEAM.equals(type) && teamIds.isEmpty()) {
                throw BizException.of(ErrorCode.BAD_REQUEST, "队伍作业必须至少指定一支队伍");
            }
            requireTeamsExist(teamIds);
        }

        LocalDate publishDate = null;
        if (TYPE_DAILY.equals(type)) {
            publishDate = req.getPublishDate() == null ? LocalDate.now() : req.getPublishDate();
            // (type, publish_date) 唯一：同一天只能有一条每日一题
            Long dup = assignmentMapper.selectCount(new LambdaQueryWrapper<Assignment>()
                    .eq(Assignment::getType, TYPE_DAILY)
                    .eq(Assignment::getPublishDate, publishDate));
            if (dup != null && dup > 0) {
                throw BizException.of(ErrorCode.DAILY_ALREADY_PUBLISHED);
            }
        }

        Assignment a = new Assignment();
        a.setType(type);
        a.setTitle(req.getTitle().trim());
        a.setDescription(req.getDescription());
        a.setProblemId(TYPE_DAILY.equals(type) ? problemIds.get(0) : null);
        a.setProblemListId(req.getProblemListId());
        // 每日一题固定一天：当天 0 点 → 次日 0 点（忽略手填的时间）
        if (TYPE_DAILY.equals(type)) {
            a.setStartAt(publishDate.atStartOfDay());
            a.setDueAt(publishDate.plusDays(1).atStartOfDay());
        } else {
            a.setStartAt(req.getStartAt());
            a.setDueAt(req.getDueAt());
        }
        a.setScoreReward(req.getScoreReward() == null ? BigDecimal.ZERO : req.getScoreReward());
        a.setAllowLateReward(req.getAllowLateReward() == null || req.getAllowLateReward());
        a.setPublishDate(publishDate);
        a.setCreatedBy(operator.getId());

        try {
            assignmentMapper.insert(a);
        } catch (DuplicateKeyException e) {
            // 并发发布两条同日每日一题时由唯一索引兜底
            throw BizException.of(ErrorCode.DAILY_ALREADY_PUBLISHED);
        }

        insertTargets(a.getId(), teamIds);
        insertProblems(a.getId(), problemIds);

        auditLogService.record(operator, "ASSIGNMENT_CREATE", "ASSIGNMENT", a.getId(),
                null, Map.of("type", type, "teams", teamIds.size(), "problems", problemIds.size()),
                "发布" + typeLabel(type) + "：" + a.getTitle());

        log.info("站主 {} 发布{}「{}」，目标队伍 {} 支，题目 {} 道",
                operator.getNickname(), typeLabel(type), a.getTitle(), teamIds.size(), problemIds.size());

        return buildVO(a, operator, true, true);
    }

    @Override
    @Transactional
    public AssignmentVO update(Long id, AssignmentRequest req, LoginUser operator) {
        Assignment a = requireAssignmentForUpdate(id);
        // 截止必须严格晚于开始
        if (!req.getDueAt().isAfter(req.getStartAt())) {
            throw BizException.of(ErrorCode.INVALID_TIME_RANGE);
        }
        List<Long> merged = resolveProblemIds(req);
        if (!merged.isEmpty()) {
            requireProblemsExist(merged);
            requireProblemSetEditable(id, new LinkedHashSet<>(merged));
        }

        Assignment patch = new Assignment();
        patch.setId(id);
        patch.setTitle(req.getTitle().trim());
        patch.setDescription(req.getDescription());
        // 每日一题固定一天：当天 0 点 → 次日 0 点（以原发布日为准）
        if (TYPE_DAILY.equals(a.getType())) {
            LocalDate publishDate = a.getPublishDate() == null
                    ? a.getStartAt().toLocalDate() : a.getPublishDate();
            patch.setStartAt(publishDate.atStartOfDay());
            patch.setDueAt(publishDate.plusDays(1).atStartOfDay());
        } else {
            patch.setStartAt(req.getStartAt());
            patch.setDueAt(req.getDueAt());
        }
        patch.setScoreReward(req.getScoreReward() == null ? a.getScoreReward() : req.getScoreReward());
        patch.setAllowLateReward(req.getAllowLateReward() == null
                ? a.getAllowLateReward() : req.getAllowLateReward());
        if (req.getProblemListId() != null) {
            patch.setProblemListId(req.getProblemListId());
        }
        if (TYPE_DAILY.equals(a.getType()) && !merged.isEmpty()) {
            patch.setProblemId(merged.get(0));
        }
        assignmentMapper.updateById(patch);

        // 题目可以整组替换
        if (!merged.isEmpty()) {
            List<Long> pids = merged;
            assignmentProblemMapper.delete(new LambdaQueryWrapper<AssignmentProblem>()
                    .eq(AssignmentProblem::getAssignmentId, id));
            insertProblems(id, pids);
        }

        // 目标队伍只在 TEAM 类型下允许改；GLOBAL 的快照一旦发布就不再变
        if (TYPE_TEAM.equals(a.getType()) && req.getTeamIds() != null) {
            List<Long> teamIds = distinct(req.getTeamIds());
            if (teamIds.isEmpty()) {
                throw BizException.of(ErrorCode.BAD_REQUEST, "队伍作业必须至少指定一支队伍");
            }
            requireTeamsExist(teamIds);
            targetMapper.delete(new LambdaQueryWrapper<AssignmentTarget>()
                    .eq(AssignmentTarget::getAssignmentId, id));
            insertTargets(id, teamIds);
        }

        auditLogService.record(operator, "ASSIGNMENT_UPDATE", "ASSIGNMENT", id,
                null, null, "编辑作业：" + patch.getTitle());

        return buildVO(requireAssignment(id), operator, true, true);
    }

    @Override
    @Transactional
    public void delete(Long id, LoginUser operator) {
        Assignment a = requireAssignmentForUpdate(id);

        long done = progressMapper.selectCount(new LambdaQueryWrapper<AssignmentProgress>()
                .eq(AssignmentProgress::getAssignmentId, id)
                .eq(AssignmentProgress::getStatus, PROG_COMPLETED));
        if (done > 0) {
            // 已经有完成记录还删，会导致积分流水指向不存在的业务对象
            throw BizException.of(ErrorCode.CONFLICT,
                    "已有 " + done + " 人完成该作业，不能删除。如需停止请把截止时间提前。");
        }

        auditLogService.record(operator, "ASSIGNMENT_DELETE", "ASSIGNMENT", id,
                Map.of("title", a.getTitle()), null, "删除作业：" + a.getTitle());

        // assignment_problem / assignment_target 有 ON DELETE CASCADE，会自动清理
        assignmentMapper.deleteById(id);
    }

    @Override
    public List<AssignmentVO> adminList(String type, boolean withDetail) {
        return loadAssignments(type).stream()
                .map(a -> buildVO(a, null, withDetail, true))
                .toList();
    }

    @Override
    public AssignmentVO adminDetail(Long id, LoginUser operator) {
        return buildVO(requireAssignment(id), operator, true, true);
    }

    /* ==================== 队员 ==================== */

    @Override
    public List<AssignmentVO> myAssignments(LoginUser me, String type) {
        List<Long> myTeams = userTeamIds(me.getId());
        Set<Long> assignable = assignmentIdsForTeams(myTeams);

        return loadAssignments(type).stream()
                .filter(a -> isVisibleToMe(a, assignable))
                .map(a -> buildVO(a, me, false, false))
                .toList();
    }

    @Override
    public AssignmentVO detail(Long id, LoginUser me) {
        Assignment a = requireAssignment(id);
        if (!me.isSiteOwner()) {
            requireVisible(a, me);
        }
        return buildVO(a, me, true, me.isSiteOwner());
    }

    /**
     * 站主贴链接批量导入题目到作业（洛谷/CF/AtCoder/牛客/力扣 链接或「平台 题号」均可）。
     * 复用批量粘贴解析器 + 占位建题逻辑。
     */
    @Override
    @Transactional
    public AssignmentVO importProblems(Long assignmentId, String text, String defaultPlatform, LoginUser me) {
        Assignment a = requireAssignmentForUpdate(assignmentId);
        if (!me.isSiteOwner()) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
        ProblemRefParser.Result r = ProblemRefParser.parse(text, defaultPlatform);
        if (r.problems().isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "没有识别到任何题目链接");
        }
        Set<Long> allProblems = problemIdsOf(assignmentId);
        for (ProblemRefParser.ParsedProblem p : r.problems()) {
            Problem problem = problemService.findOrCreate(p.platform(), p.problemNo(), p.url(), me.getId());
            if (problem != null && !allProblems.contains(problem.getId())) {
                Set<Long> revised = new LinkedHashSet<>(allProblems);
                revised.add(problem.getId());
                requireProblemSetEditable(assignmentId, revised);
                AssignmentProblem ap = new AssignmentProblem();
                ap.setAssignmentId(assignmentId);
                ap.setProblemId(problem.getId());
                try {
                    assignmentProblemMapper.insert(ap);
                    allProblems.add(problem.getId());
                } catch (org.springframework.dao.DuplicateKeyException ignored) {
                    // 已在作业里，跳过
                }
            }
        }
        return buildVO(a, me, true, me.isSiteOwner());
    }

    @Override
    @Transactional
    public AssignmentVO markProblemDone(Long assignmentId, Long problemId, LoginUser me) {
        Assignment a = requireAssignmentForUpdate(assignmentId);
        requireVisible(a, me);

        LocalDateTime now = LocalDateTime.now();
        String ts = AssignmentStatus.timeStatus(a.getStartAt(), a.getDueAt(), now);
        if (AssignmentStatus.NOT_STARTED.equals(ts)) {
            throw BizException.of(ErrorCode.ASSIGNMENT_NOT_STARTED);
        }
        // 注意：逾期后**仍然允许**标记完成，只是会记录为「逾期完成」，
        //       是否给分由 allowLateReward 决定（计划书 4.4）。

        Set<Long> allProblems = problemIdsOf(assignmentId);
        if (!allProblems.contains(problemId)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "该题目不属于这个作业");
        }

        // ---- 有效提交校验（用户要求：AC 了才算完成，杜绝点一下就过）----
        // 必须存在该用户在该题的真实 AC 记录（爬虫同步 / 洛谷导入 / 站主登记），
        // 且提交时间不早于作业开始时间——历史旧 AC 不算数。
        Long acCount = submissionMapper.selectCount(new LambdaQueryWrapper<SubmissionRecord>()
                .eq(SubmissionRecord::getUserId, me.getId())
                .eq(SubmissionRecord::getProblemId, problemId)
                .eq(SubmissionRecord::getResult, "AC")
                .ge(SubmissionRecord::getSubmittedAt, a.getStartAt()));
        if (acCount == null || acCount == 0) {
            throw BizException.of(ErrorCode.BAD_REQUEST,
                    "尚未检测到你在本题的 AC 记录：请先去 OJ 通过本题，"
                            + "然后到个人中心点「刷新做题记录」同步，再回来验证。");
        }

        AssignmentProgress prog = getOrCreateProgress(a, me, allProblems.size());

        // ---- 重算作业进度 ----
        Set<Long> done = doneProblemIds(me.getId(), allProblems);
        prog.setCompletedCount(done.size());
        prog.setTotalCount(allProblems.size());
        prog.setTeamId(myTeamsOf(me.getId(), a.getId()));

        finalizeProgress(a, me, prog, allProblems, done, now);

        return buildVO(a, me, true, false);
    }

    @Override
    @Transactional
    public AssignmentVO completeAssignment(Long assignmentId, LoginUser me) {
        Assignment a = requireAssignmentForUpdate(assignmentId);
        requireVisible(a, me);

        LocalDateTime now = LocalDateTime.now();
        String ts = AssignmentStatus.timeStatus(a.getStartAt(), a.getDueAt(), now);
        if (AssignmentStatus.NOT_STARTED.equals(ts)) {
            throw BizException.of(ErrorCode.ASSIGNMENT_NOT_STARTED);
        }

        Set<Long> allProblems = problemIdsOf(assignmentId);
        Set<Long> done = doneProblemIds(me.getId(), allProblems);
        if (!AssignmentStatus.allDone(done, allProblems)) {
            throw BizException.of(ErrorCode.BAD_REQUEST,
                    "还有 " + (allProblems.size() - done.size()) + " 道题未完成，无法结算");
        }

        AssignmentProgress prog = getOrCreateProgress(a, me, allProblems.size());
        prog.setCompletedCount(done.size());
        prog.setTotalCount(allProblems.size());
        prog.setTeamId(myTeamsOf(me.getId(), assignmentId));

        finalizeProgress(a, me, prog, allProblems, done, now);

        return buildVO(a, me, true, false);
    }

    /**
     * 落库进度；若全部题目已完成且尚未结算，则正式结算并发分。
     *
     * 抽出来是因为 markProblemDone 与 completeAssignment 走的是同一套收尾逻辑，
     * 两处各写一遍很容易只改一处（尤其是幂等键）而引入重复发分。
     */
    private void finalizeProgress(Assignment a, LoginUser me, AssignmentProgress prog,
                                  Set<Long> allProblems, Set<Long> done, LocalDateTime now) {
        boolean allDone = AssignmentStatus.allDone(done, allProblems);
        boolean justCompleted = false;

        if (allDone && !PROG_COMPLETED.equals(prog.getStatus())) {
            prog.setStatus(PROG_COMPLETED);
            prog.setCompletedAt(now);
            prog.setLate(AssignmentStatus.isPastDue(a.getDueAt(), now));
            justCompleted = true;
        }

        if (prog.getId() == null) {
            progressMapper.insert(prog);
        } else {
            progressMapper.updateById(prog);
        }

        if (justCompleted) {
            boolean late = Boolean.TRUE.equals(prog.getLate());
            boolean rewardAllowed = !late || Boolean.TRUE.equals(a.getAllowLateReward());
            if (rewardAllowed) {
                grantCompletionReward(a, me.getId());
            } else {
                log.info("作业 {} 逾期完成且配置为不给分，用户 {}", a.getId(), me.getId());
            }
        }
    }

    /** 结算完成奖励。每日一题走 DAILY_SOLVE，其余走 ASSIGNMENT_DONE，二者不叠加。 */
    private void grantCompletionReward(Assignment a, Long userId) {
        BigDecimal reward = a.getScoreReward() == null ? BigDecimal.ZERO : a.getScoreReward();
        boolean zero = reward.compareTo(BigDecimal.ZERO) == 0;
        if (TYPE_DAILY.equals(a.getType())) {
            scoreService.grantDailySolved(userId,
                    a.getPublishDate() == null ? LocalDate.now() : a.getPublishDate(),
                    zero ? FALLBACK_DAILY : reward);
        } else {
            scoreService.grantAssignmentDone(userId, a.getId(),
                    zero ? FALLBACK_ASSIGNMENT : reward);
        }
    }

    /* ==================== 每日一题 ==================== */

    @Override
    public AssignmentVO todayDaily(LoginUser me) {
        LocalDate today = LocalDate.now();
        Assignment a = assignmentMapper.selectOne(new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getType, TYPE_DAILY)
                .eq(Assignment::getPublishDate, today));
        if (a == null) {
            return null;
        }
        // 每日一题若限定了队伍，也要过滤
        if (!me.isSiteOwner()) {
            Set<Long> assignable = assignmentIdsForTeams(userTeamIds(me.getId()));
            if (!isVisibleToMe(a, assignable)) {
                return null;
            }
        }
        return buildVO(a, me, true, false);
    }

    @Override
    public List<AssignmentVO> dailyHistory(LoginUser me, int limit) {
        List<Long> myTeams = userTeamIds(me.getId());
        Set<Long> assignable = assignmentIdsForTeams(myTeams);

        return assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                        .eq(Assignment::getType, TYPE_DAILY)
                        .orderByDesc(Assignment::getPublishDate)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 90)))
                .stream()
                .filter(a -> me.isSiteOwner() || isVisibleToMe(a, assignable))
                .map(a -> buildVO(a, me, true, false))
                .toList();
    }

    /* ==================== 视图组装 ==================== */

    private AssignmentVO buildVO(Assignment a, LoginUser viewer, boolean withProblems, boolean withStats) {
        LocalDateTime now = LocalDateTime.now();
        String ts = AssignmentStatus.timeStatus(a.getStartAt(), a.getDueAt(), now);

        Long aid = a.getId();
        List<AssignmentTarget> targets = targetMapper.selectList(
                new LambdaQueryWrapper<AssignmentTarget>().eq(AssignmentTarget::getAssignmentId, aid));

        List<AssignmentVO.TeamRef> teamRefs = Collections.emptyList();
        if (!targets.isEmpty()) {
            List<Long> tids = targets.stream().map(AssignmentTarget::getTeamId).toList();
            Map<Long, Team> teams = teamMapper.selectBatchIds(tids).stream()
                    .collect(Collectors.toMap(Team::getId, Function.identity()));
            teamRefs = tids.stream()
                    .map(teams::get)
                    .filter(java.util.Objects::nonNull)
                    .map(t -> AssignmentVO.TeamRef.builder().id(t.getId()).name(t.getName()).build())
                    .toList();
        }

        Set<Long> allProblems = problemIdsOf(aid);

        List<ProblemVO> problems = Collections.emptyList();
        Set<Long> doneIds = viewer == null ? Set.of() : doneProblemIds(viewer.getId(), allProblems);

        if (withProblems) {
            if (allProblems.isEmpty()) {
                problems = List.of();
            } else {
                Map<Long, Problem> pmap = problemMapper.selectBatchIds(allProblems).stream()
                        .collect(Collectors.toMap(Problem::getId, Function.identity()));
                List<AssignmentProblem> order = assignmentProblemMapper.selectList(
                        new LambdaQueryWrapper<AssignmentProblem>()
                                .eq(AssignmentProblem::getAssignmentId, aid)
                                .orderByAsc(AssignmentProblem::getSortOrder));
                problems = order.stream()
                        .map(ap -> pmap.get(ap.getProblemId()))
                        .filter(java.util.Objects::nonNull)
                        .map(p -> ProblemVO.builder()
                                .id(p.getId())
                                .title(p.getTitle())
                                .platform(p.getPlatform())
                                .problemNo(p.getProblemNo())
                                .url(p.getUrl())
                                .difficulty(p.getDifficulty())
                                .tags(p.getTags())
                                .done(doneIds.contains(p.getId()))
                                // 这题按难度档位值多少分，前端直接显示，省得再问一次接口
                                .score(difficultyService.resolveScore(
                                        p.getPlatform(), p.getDifficulty(), FALLBACK_FIRST_AC))
                                .build())
                        .toList();
            }
        }

        AssignmentVO.MyProgress mine = null;
        if (viewer != null) {
            AssignmentProgress prog = progressMapper.selectOne(new LambdaQueryWrapper<AssignmentProgress>()
                    .eq(AssignmentProgress::getAssignmentId, aid)
                    .eq(AssignmentProgress::getUserId, viewer.getId()));

            boolean completed = prog != null && PROG_COMPLETED.equals(prog.getStatus());
            boolean late = prog != null && Boolean.TRUE.equals(prog.getLate());

            mine = AssignmentVO.MyProgress.builder()
                    .rawStatus(prog == null ? PROG_IN_PROGRESS : prog.getStatus())
                    .status(AssignmentStatus.displayStatus(ts, completed, late))
                    .late(late)
                    .completedCount(doneIds.size())
                    .totalCount(allProblems.size())
                    .completedAt(prog == null ? null : prog.getCompletedAt())
                    .doneProblemIds(doneIds)
                    .scored(hasScored(a, viewer.getId()))
                    .build();
        }

        AssignmentVO.AdminStats stats = null;
        if (withStats) {
            List<Long> targetTeamIds = targets.stream().map(AssignmentTarget::getTeamId).toList();
            int targetUsers = targetTeamIds.isEmpty() && TYPE_DAILY.equals(a.getType())
                    ? Math.toIntExact(userMapper.selectCount(new LambdaQueryWrapper<User>()
                            .eq(User::getStatus, "ACTIVE")))
                    : (int) countActiveMembers(targetTeamIds);
            long completedUsers = progressMapper.selectCount(new LambdaQueryWrapper<AssignmentProgress>()
                    .eq(AssignmentProgress::getAssignmentId, aid)
                    .eq(AssignmentProgress::getStatus, PROG_COMPLETED));
            boolean pastDue = AssignmentStatus.isPastDue(a.getDueAt(), now);
            stats = AssignmentVO.AdminStats.builder()
                    .targetTeams(targetTeamIds.size())
                    .targetUsers(targetUsers)
                    .completedUsers((int) completedUsers)
                    .overdueUsers(pastDue ? Math.max(0, targetUsers - (int) completedUsers) : 0)
                    .build();
        }

        return AssignmentVO.builder()
                .id(aid)
                .type(a.getType())
                .title(a.getTitle())
                .description(a.getDescription())
                .startAt(a.getStartAt())
                .dueAt(a.getDueAt())
                .scoreReward(a.getScoreReward())
                .allowLateReward(a.getAllowLateReward())
                .publishDate(a.getPublishDate())
                .createdAt(a.getCreatedAt())
                .timeStatus(ts)
                .secondsUntilDue(secondsUntil(a.getDueAt(), now))
                .secondsUntilStart(secondsUntil(a.getStartAt(), now))
                .targets(teamRefs)
                .targetTeamCount(teamRefs.size())
                .problems(problems)
                .totalCount(allProblems.size())
                .mine(mine)
                .stats(stats)
                .build();
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 合并「直接给的题目」与「从题单导入的题目」，去重。
     *
     * 允许两者同时给：站主可以先选一个题单打底，再手工加几道。
     */
    private List<Long> resolveProblemIds(AssignmentRequest req) {
        Set<Long> ids = new LinkedHashSet<>();
        if (req.getProblemIds() != null) {
            ids.addAll(req.getProblemIds());
        }
        if (req.getProblemListId() != null) {
            List<Long> fromList = problemListService.problemIdsOf(req.getProblemListId());
            if (fromList.isEmpty()) {
                throw BizException.of(ErrorCode.BAD_REQUEST, "所选题单里还没有题目");
            }
            ids.addAll(fromList);
            // 把来源题单记下来，便于追溯这份作业的题是从哪来的
            if (req.getProblemIds() == null || req.getProblemIds().isEmpty()) {
                req.setProblemIds(new ArrayList<>(ids));
            }
        }
        return new ArrayList<>(ids);
    }

    private List<Assignment> loadAssignments(String type) {
        LambdaQueryWrapper<Assignment> q = new LambdaQueryWrapper<Assignment>()
                .orderByDesc(Assignment::getCreatedAt)
                .orderByDesc(Assignment::getId);
        if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
            q.eq(Assignment::getType, normalizeType(type));
        }
        return assignmentMapper.selectList(q);
    }

    /**
     * 当前用户可见的作业 id。
     * 取「我所属队伍被指派到的作业」集合，再由 isVisibleToMe 叠加「无目标作业」。
     */
    private Set<Long> assignmentIdsForTeams(List<Long> teamIds) {
        if (teamIds.isEmpty()) {
            return Set.of();
        }
        return targetMapper.selectList(new LambdaQueryWrapper<AssignmentTarget>()
                        .in(AssignmentTarget::getTeamId, teamIds))
                .stream().map(AssignmentTarget::getAssignmentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 可见性判定：
     *   - 作业有目标队伍 → 我所在的某支队伍在目标里才可见
     *   - 作业没有目标队伍 → 视为面向所有人（每日一题不限队伍的情况）
     */
    private boolean isVisibleToMe(Assignment a, Set<Long> myAssignableIds) {
        if (myAssignableIds.contains(a.getId())) {
            return true;
        }
        return TYPE_DAILY.equals(a.getType()) && targetMapper.selectCount(new LambdaQueryWrapper<AssignmentTarget>()
                .eq(AssignmentTarget::getAssignmentId, a.getId())) == 0;
    }

    private void requireVisible(Assignment a, LoginUser me) {
        List<Long> myTeams = userTeamIds(me.getId());
        if (!isVisibleToMe(a, assignmentIdsForTeams(myTeams))) {
            throw BizException.of(ErrorCode.NOT_YOUR_ASSIGNMENT);
        }
    }

    private AssignmentProgress getOrCreateProgress(Assignment a, LoginUser me, int total) {
        AssignmentProgress prog = progressMapper.selectOne(new LambdaQueryWrapper<AssignmentProgress>()
                .eq(AssignmentProgress::getAssignmentId, a.getId())
                .eq(AssignmentProgress::getUserId, me.getId()));
        if (prog != null) {
            return prog;
        }
        prog = new AssignmentProgress();
        prog.setAssignmentId(a.getId());
        prog.setUserId(me.getId());
        prog.setStatus(PROG_IN_PROGRESS);
        prog.setLate(false);
        prog.setCompletedCount(0);
        prog.setTotalCount(total);
        return prog;
    }

    private Set<Long> doneProblemIds(Long userId, Set<Long> scope) {
        if (scope.isEmpty()) {
            return Set.of();
        }
        return submissionMapper.selectList(new LambdaQueryWrapper<SubmissionRecord>()
                        .eq(SubmissionRecord::getUserId, userId)
                        .eq(SubmissionRecord::getResult, "AC")
                        .in(SubmissionRecord::getProblemId, scope))
                .stream().map(SubmissionRecord::getProblemId)
                .collect(Collectors.toSet());
    }

    /** 该项奖励是否已经发过，避免前端显示「可加分」但实际早已结算 */
    private boolean hasScored(Assignment a, Long userId) {
        String key = TYPE_DAILY.equals(a.getType())
                ? "daily:" + (a.getPublishDate() == null ? LocalDate.now() : a.getPublishDate()) + ":" + userId
                : "assignment:" + a.getId() + ":" + userId;
        return scoreEventMapper.selectCount(new LambdaQueryWrapper<ScoreEvent>()
                .eq(ScoreEvent::getIdempotentKey, key)) > 0;
    }

    private Set<Long> problemIdsOf(Long assignmentId) {
        return assignmentProblemMapper.selectList(new LambdaQueryWrapper<AssignmentProblem>()
                        .eq(AssignmentProblem::getAssignmentId, assignmentId)
                        .orderByAsc(AssignmentProblem::getSortOrder))
                .stream().map(AssignmentProblem::getProblemId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Long> userTeamIds(Long userId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, "ACTIVE"))
                .stream().map(TeamMember::getTeamId).toList();
    }

    /** 提交进度时记录所属队伍，便于按队统计 */
    private Long myTeamsOf(Long userId, Long assignmentId) {
        List<Long> myTeams = userTeamIds(userId);
        if (myTeams.isEmpty()) {
            return null;
        }
        List<AssignmentTarget> targets = targetMapper.selectList(
                new LambdaQueryWrapper<AssignmentTarget>()
                        .eq(AssignmentTarget::getAssignmentId, assignmentId)
                        .in(AssignmentTarget::getTeamId, myTeams));
        return targets.isEmpty() ? myTeams.get(0) : targets.get(0).getTeamId();
    }

    private long countActiveMembers(List<Long> teamIds) {
        if (teamIds.isEmpty()) {
            return 0;
        }
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .in(TeamMember::getTeamId, teamIds)
                .eq(TeamMember::getStatus, "ACTIVE"))
                .stream().map(TeamMember::getUserId).distinct().count();
    }

    private void insertTargets(Long assignmentId, List<Long> teamIds) {
        for (Long tid : teamIds) {
            AssignmentTarget t = new AssignmentTarget();
            t.setAssignmentId(assignmentId);
            t.setTeamId(tid);
            targetMapper.insert(t);
        }
    }

    private void insertProblems(Long assignmentId, List<Long> problemIds) {
        int order = 0;
        for (Long pid : problemIds) {
            AssignmentProblem ap = new AssignmentProblem();
            ap.setAssignmentId(assignmentId);
            ap.setProblemId(pid);
            ap.setSortOrder(order++);
            assignmentProblemMapper.insert(ap);
        }
    }

    private void requireProblemsExist(List<Long> ids) {
        long found = problemMapper.selectCount(new LambdaQueryWrapper<Problem>().in(Problem::getId, ids));
        if (found != ids.size()) {
            throw BizException.of(ErrorCode.PROBLEM_NOT_FOUND, "部分题目不存在，请刷新后重试");
        }
    }

    private void requireTeamsExist(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        long found = teamMapper.selectCount(new LambdaQueryWrapper<Team>().in(Team::getId, ids));
        if (found != ids.size()) {
            throw BizException.of(ErrorCode.TEAM_NOT_FOUND, "部分队伍不存在，请刷新后重试");
        }
    }

    private Assignment requireAssignment(Long id) {
        Assignment a = assignmentMapper.selectById(id);
        if (a == null) {
            throw BizException.of(ErrorCode.ASSIGNMENT_NOT_FOUND);
        }
        return a;
    }

    private void requireProblemSetEditable(Long assignmentId, Set<Long> revised) {
        if (!problemIdsOf(assignmentId).equals(revised)
                && progressMapper.selectCount(new LambdaQueryWrapper<AssignmentProgress>()
                .eq(AssignmentProgress::getAssignmentId, assignmentId)
                .eq(AssignmentProgress::getStatus, PROG_COMPLETED)) > 0) {
            throw BizException.of(ErrorCode.CONFLICT,
                    "该作业已有完成记录，不能增删题目；可以修改标题、说明或调整原题目的顺序。");
        }
    }

    private Assignment requireAssignmentForUpdate(Long id) {
        Assignment a = assignmentMapper.selectByIdForUpdate(id);
        if (a == null) {
            throw BizException.of(ErrorCode.ASSIGNMENT_NOT_FOUND);
        }
        return a;
    }

    private static String normalizeType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!TYPE_TEAM.equals(t) && !TYPE_GLOBAL.equals(t) && !TYPE_DAILY.equals(t)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "作业类型只能是 TEAM / GLOBAL / DAILY");
        }
        return t;
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case TYPE_TEAM -> "队伍作业";
            case TYPE_GLOBAL -> "全体作业";
            default -> "每日一题";
        };
    }

    private static List<Long> distinct(List<Long> in) {
        if (in == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(in));
    }

    private static Long secondsUntil(LocalDateTime target, LocalDateTime now) {
        if (target == null) {
            return null;
        }
        return Duration.between(now, target).getSeconds();
    }
}
