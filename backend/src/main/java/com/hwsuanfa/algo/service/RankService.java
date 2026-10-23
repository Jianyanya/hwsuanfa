package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.vo.RankVO;

/** 积分排行榜（个人 + 队伍） */
public interface RankService {

    /** 全量排行：个人榜 + 队伍榜一次返回（量级小，前端两个 tab 共用） */
    RankVO leaderboard();
}
