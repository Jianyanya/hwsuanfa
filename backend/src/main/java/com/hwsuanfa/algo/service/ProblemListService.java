package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.common.ProblemRefParser;
import com.hwsuanfa.algo.dto.ProblemImportRequest;
import com.hwsuanfa.algo.dto.ProblemListRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.vo.ProblemListVO;

import java.util.List;

/**
 * 题单。
 *
 * 定位提醒：我们**不是 OJ**。题单只记录「这份训练包含哪些题」，
 * 存的是平台 + 题号的**引用**，不存题面、不判题。
 * 因此批量粘贴导入是最主要的录题方式 —— 标题可以留空（用题号占位）。
 *
 * 权限：
 *   - 个人题单：本人可改
 *   - 队伍题单：站主、本队队长可改；普通队员新建的进 PENDING 待审核
 */
public interface ProblemListService {

    /* ---------- 读 ---------- */

    /** 我能看到的题单：我的个人题单 + 我所在队伍的题单 */
    List<ProblemListVO> myLists(LoginUser me);

    /** 详情（带题目明细） */
    ProblemListVO detail(Long id, LoginUser me);

    /** 站主后台：全部题单，可按 scope 过滤 */
    List<ProblemListVO> adminList(String scope);

    /* ---------- 写 ---------- */

    ProblemListVO create(ProblemListRequest req, LoginUser me);

    ProblemListVO update(Long id, ProblemListRequest req, LoginUser me);

    void delete(Long id, LoginUser me);

    /* ---------- 题目 ---------- */

    /**
     * 批量加入题目。支持给 id，也支持直接粘文本。
     *
     * @return 导入后的题单详情
     */
    ProblemListVO importProblems(Long id, ProblemImportRequest req, LoginUser me);

    /** 仅解析预览，不落库。给前端做「粘贴后先看看能识别出什么」 */
    ProblemRefParser.Result previewImport(String text, String defaultPlatform);

    ProblemListVO removeProblem(Long id, Long problemId, LoginUser me);

    /** 按给定顺序重排（只允许题目集合等价的重排，避免误删） */
    ProblemListVO reorder(Long id, List<Long> problemIds, LoginUser me);

    /* ---------- 审核 ---------- */

    ProblemListVO audit(Long id, boolean approve, String rejectReason, LoginUser me);

    /* ---------- 作业联动 ---------- */

    /** 取题单里的题目 id（作业从题单导入用） */
    List<Long> problemIdsOf(Long listId);
}
