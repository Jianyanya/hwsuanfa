package com.hwsuanfa.algo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 排行榜聚合查询。
 *
 * 设计说明：直接从 score_event / submission_record 聚合，**不走 user_score_summary 汇总表**。
 * 汇总表需要每次发分时同步维护，漏一处就脏数据；站内事件量级（千级）直接聚合毫秒出结果，
 * 等量级上来了再上汇总表也不迟。故意留了这个简单。
 */
@Mapper
public interface RankQueryMapper {

    String SCORE_AGG = """
        SELECT user_id,
               SUM(score) AS total,
               SUM(event_type = 'FIRST_AC') AS first_ac,
               SUM(event_type = 'DAILY_SOLVE') AS daily_cnt,
               SUM(event_type = 'ASSIGNMENT_DONE') AS assignment_cnt,
               SUM(event_type IN ('CONTEST_JOIN', 'CONTEST_RANK')) AS contest_cnt
        FROM score_event
        GROUP BY user_id
        """;

    /** 个人排行（不含完全私密的用户） */
    @Select("""
        SELECT u.id AS userId, u.nickname AS nickname, u.class_name AS className,
               COALESCE(ev.total, 0) AS totalScore,
               COALESCE(ev.first_ac, 0) AS firstAc,
               COALESCE(ev.daily_cnt, 0) AS dailyCount,
               COALESCE(ev.assignment_cnt, 0) AS assignmentCount,
               COALESCE(ev.contest_cnt, 0) AS contestCount,
               COALESCE(sub.solved, 0) AS solvedCount
        FROM user u
        LEFT JOIN (""" + SCORE_AGG + """
        ) ev ON ev.user_id = u.id
        LEFT JOIN (
            SELECT user_id, COUNT(DISTINCT problem_id) AS solved
            FROM submission_record WHERE result = 'AC'
            GROUP BY user_id
        ) sub ON sub.user_id = u.id
        WHERE u.status = 'ACTIVE'
          AND (u.privacy_level IS NULL OR u.privacy_level <> 'PRIVATE')
        ORDER BY totalScore DESC, u.id ASC
        """)
    List<Map<String, Object>> selectUserLeaderboard();

    /** 队伍排行（队员积分求和） */
    @Select("""
        SELECT t.id AS teamId, t.name AS teamName, t.leader_user_id AS leaderUserId,
               COUNT(tm.user_id) AS memberCount,
               COALESCE(SUM(COALESCE(ev.total, 0)), 0) AS totalScore
        FROM team t
        JOIN team_member tm ON tm.team_id = t.id AND tm.status = 'ACTIVE'
        JOIN user u ON u.id = tm.user_id AND u.status = 'ACTIVE'
             AND (u.privacy_level IS NULL OR u.privacy_level <> 'PRIVATE')
        LEFT JOIN (""" + SCORE_AGG + """
        ) ev ON ev.user_id = tm.user_id
        WHERE t.status = 'ACTIVE'
        GROUP BY t.id, t.name, t.leader_user_id
        ORDER BY totalScore DESC, t.id ASC
        """)
    List<Map<String, Object>> selectTeamLeaderboard();
}
