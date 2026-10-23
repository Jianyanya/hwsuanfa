package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.dto.ContestLogRequest;
import com.hwsuanfa.algo.dto.ContestRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.vo.ContestVO;

import java.util.List;

/**
 * 比赛。
 *
 * 定位（用户确认）：**手动登记，不抓取**。「我们队要参加哪场比赛」是教学决策，
 * 抓来的全球比赛列表没有意义。排名与题目永远在 OJ 上，这里只存引用与结果。
 *
 * 权限：
 *   - 登记比赛：站主
 *   - 选择参赛：本队队长或站主（选择即按等级发放参赛积分，幂等）
 *   - 比赛日志：本队成员可提交（进待审核），本队队长或站主确认
 *     （确认后向实际参赛成员发名次分，幂等）
 */
public interface ContestService {

    /* ---------- 站主 ---------- */

    ContestVO create(ContestRequest req, LoginUser operator);

    ContestVO update(Long id, ContestRequest req, LoginUser operator);

    void delete(Long id, LoginUser operator);

    List<ContestVO> adminList();

    /* ---------- 队员 / 队长 ---------- */

    /** 我能看到的比赛：全部比赛（比赛本身是公开信息），带本队是否参赛标记 */
    List<ContestVO> list(LoginUser me);

    ContestVO detail(Long id, LoginUser me);

    /** 队长/站主为本队选择参赛，按比赛等级向全体在队成员发参赛分 */
    ContestVO selectParticipation(Long contestId, List<Long> teamIds, LoginUser me);

    /** 取消参赛（留痕，不删行）。不追回已发的参赛分 */
    ContestVO cancelParticipation(Long contestId, Long teamId, LoginUser me);

    /** 提交比赛日志。队长/站主直接确认并发名次分；普通队员提交进待审核 */
    ContestVO submitLog(Long contestId, ContestLogRequest req, LoginUser me);

    /** 队长/站主确认日志 → 发名次分 */
    ContestVO confirmLog(Long contestId, Long logId, LoginUser me);

    /** 删除日志（仅站主，且未确认的才能删） */
    void deleteLog(Long contestId, Long logId, LoginUser operator);

    /* ---------- 个人参赛 ---------- */

    /**
     * 以个人身份报名。
     * 规则：本队已经报名这场比赛时，成员不能再以个人身份参加。
     */
    ContestVO joinIndividually(Long contestId, LoginUser me);

    /** 取消个人报名（不追回已发的参赛分） */
    ContestVO cancelIndividualJoin(Long contestId, LoginUser me);

    /* ---------- 榜单 ---------- */

    /**
     * 从平台官方榜单抓成绩并落到参赛记录上。
     * Codeforces 走官方 API；其他平台没有公开接口，会明确报错回落手动录入。
     * 只处理我们自己的报名者（按 handle 匹配），榜单上的其他选手不入库。
     */
    ContestVO syncStandings(Long contestId, LoginUser operator);

    /** 手动补一条成绩（CF 以外平台用） */
    ContestVO submitManualScore(Long contestId, ContestLogRequest req, LoginUser me);

    /* ---------- 比赛记录 ---------- */

    /**
     * 我的比赛记录：个人参加的 + 本队参加的。
     *
     * @param range    7d / 1m / 1y / all（按比赛开始时间过滤）
     * @param platform 平台过滤，空为全部
     */
    List<ContestVO> records(LoginUser me, String range, String platform);
}
