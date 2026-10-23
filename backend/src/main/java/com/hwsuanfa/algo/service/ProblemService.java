package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.dto.ProblemRequest;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.vo.ProblemVO;

import java.util.List;
import java.util.Map;

public interface ProblemService {

    List<ProblemVO> search(String keyword, String platform, int limit);

    ProblemVO detail(Long id);

    ProblemVO create(ProblemRequest req, LoginUser operator);

    ProblemVO update(Long id, ProblemRequest req, LoginUser operator);

    /** 题目被作业引用时不给删，避免作业出现空题 */
    void delete(Long id);

    /** 批量取题目（作业详情用），返回 id -> VO */
    Map<Long, Problem> loadByIds(List<Long> ids);

    /**
     * 按 (平台, 题号) 找题目，找不到就用题号当标题建一条占位记录。
     *
     * 这是「批量粘贴导入」的关键：粘贴时用户只给平台和题号，
     * 我们**不抓题目**，标题先用题号占位，之后可以人工补充。
     */
    Problem findOrCreate(String platform, String problemNo, String url, Long operatorId);
}
