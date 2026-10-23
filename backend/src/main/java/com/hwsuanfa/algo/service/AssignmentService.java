package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.dto.AssignmentRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.vo.AssignmentVO;

import java.util.List;

/**
 * 作业 / 每日一题业务。
 *
 * 硬性规则：
 *   1. **状态由后端按服务器时间实时计算**，表里不存 status，客户端时钟不可信。
 *   2. **全体作业保存发布时的队伍快照**（assignment_target），
 *      发布后新增的队伍不受影响，历史范围也不变。
 *   3. 同一作业的完成奖励**只发一次**（幂等键 assignment:{id}:{userId}）。
 *   4. 每日一题同一天只能有一条；同一用户每天只拿一次奖励。
 *   5. 队员只能看到被指派给自己所在队伍的作业。
 */
public interface AssignmentService {

    /* ---------------- 站主 ---------------- */

    AssignmentVO create(AssignmentRequest req, LoginUser operator);

    AssignmentVO update(Long id, AssignmentRequest req, LoginUser operator);

    void delete(Long id, LoginUser operator);

    /** 站主列表：默认全部类型，可按 type 过滤 */
    List<AssignmentVO> adminList(String type, boolean withDetail);

    /** 站主详情：带题目清单与完成统计 */
    AssignmentVO adminDetail(Long id, LoginUser operator);

    /* ---------------- 队员 ---------------- */

    /** 我的作业：只返回指派给我所在队伍的（含快照判断） */
    List<AssignmentVO> myAssignments(LoginUser me, String type);

    /** 作业详情（会校验可见性） */
    AssignmentVO detail(Long id, LoginUser me);

    /** 站主：贴链接批量导入题目到作业 */
    AssignmentVO importProblems(Long assignmentId, String text, String defaultPlatform, LoginUser me);

    AssignmentVO markProblemDone(Long assignmentId, Long problemId, LoginUser me);

    /**
     * 结算作业：把已经全部完成、但尚未结算的作业正式标记为完成并发分。
     *
     * 为什么需要单独一个接口：题目完成状态是**全局**的（同一道题在哪份作业里做都算做过），
     * 所以会出现「作业里的题早就做完了，但作业还没结算」的情况 ——
     * 此时每道题都显示已完成，没有「标记完成」按钮可按，作业就会永远停在未结算。
     */
    AssignmentVO completeAssignment(Long assignmentId, LoginUser me);

    /* ---------------- 每日一题 ---------------- */

    /** 今日一题；未发布返回 null */
    AssignmentVO todayDaily(LoginUser me);

    /** 历史每日一题 */
    List<AssignmentVO> dailyHistory(LoginUser me, int limit);
}
