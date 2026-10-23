package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.dto.AddMemberRequest;
import com.hwsuanfa.algo.dto.TeamCreateRequest;
import com.hwsuanfa.algo.dto.TeamUpdateRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.vo.TeamBriefVO;
import com.hwsuanfa.algo.vo.TeamVO;

import java.util.List;

/**
 * 队伍业务。
 *
 * 权限边界（与计划书 2.1 / 2.2 一致）：
 *   - **队伍结构类操作全部仅站主可执行**：建队、改名、冻结/解冻、解散、
 *     添加/移除成员、变更队长。
 *   - 队长对本队是**只读**（本阶段），并可管理本队题单/比赛日志（后续阶段）。
 *     计划书明确「队长更换只能由站主完成」，成员增减也归在站主权限下。
 *
 * 硬性规则（每条都有对应测试）：
 *   1. 一个队伍有且仅有一名队长 —— leader_user_id 单列天然唯一
 *   2. 队长必须是本队 ACTIVE 成员
 *   3. 队伍成员上限 = team.max_members（当前 3），并发下也不能超员
 *   4. 移除队长前必须先指定新队长
 *   5. 队伍仅剩队长一人时，不能移除该队长，只能先指定新队长或解散
 */
public interface TeamService {

    /* ---------------- 读 ---------------- */

    /** 站主后台：全部队伍概览 */
    List<TeamBriefVO> listAll();

    /** 当前用户所属队伍（侧栏与「我的队伍」用） */
    List<TeamBriefVO> myTeams(LoginUser viewer);

    /**
     * 队伍详情。字段按访问者身份裁剪：
     * 非站主看不到成员的真实姓名与学号。
     */
    TeamVO detail(Long teamId, LoginUser viewer);

    /* ---------------- 站主写操作 ---------------- */

    TeamVO create(TeamCreateRequest req, LoginUser operator);

    TeamVO update(Long teamId, TeamUpdateRequest req, LoginUser operator);

    TeamVO addMember(Long teamId, AddMemberRequest req, LoginUser operator);

    TeamVO removeMember(Long teamId, Long userId, Long newLeaderId, LoginUser operator);

    TeamVO changeLeader(Long teamId, Long newLeaderId, LoginUser operator);

    void freeze(Long teamId, String reason, LoginUser operator);

    void unfreeze(Long teamId, LoginUser operator);

    /**
     * 解散队伍。有业务痕迹（作业/比赛）时**拒绝**，提示改为冻结 ——
     * 计划书要求不能直接抹掉历史任务与积分记录。
     */
    void dissolve(Long teamId, LoginUser operator);
}
