package com.hwsuanfa.algo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hwsuanfa.algo.entity.Team;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeamMapper extends BaseMapper<Team> {

    /**
     * 加行锁读取队伍，用于「成员数不能超过上限」的并发保护。
     *
     * ⚠️ 必须在 @Transactional 内调用，事务提交/回滚时才释放锁；
     * 否则锁立即失效，起不到防超员的作用。
     * 两个并发请求会因争抢同一行而串行化，从而不会双双向已满队伍插入成员。
     */
    @Select("SELECT * FROM team WHERE id = #{id} FOR UPDATE")
    Team selectByIdForUpdate(@Param("id") Long id);

    /* ---------------- 解散前的历史数据检查 ----------------
       计划书要求：站主可以冻结队伍，但不直接删除历史任务和积分记录。
       所以解散前必须确认该队没有业务痕迹，有痕迹就应当改为冻结。
       ------------------------------------------------------ */

    @Select("SELECT COUNT(*) FROM assignment_target WHERE team_id = #{teamId}")
    int countAssignmentTargets(@Param("teamId") Long teamId);

    @Select("SELECT COUNT(*) FROM assignment_progress WHERE team_id = #{teamId}")
    int countAssignmentProgress(@Param("teamId") Long teamId);

    @Select("SELECT COUNT(*) FROM contest_log WHERE team_id = #{teamId}")
    int countContestLogs(@Param("teamId") Long teamId);

    @Select("SELECT COUNT(*) FROM team_contest WHERE team_id = #{teamId} AND status = 'SELECTED'")
    int countSelectedContests(@Param("teamId") Long teamId);

    @Select("SELECT COUNT(*) FROM problem_list WHERE team_id = #{teamId}")
    int countProblemLists(@Param("teamId") Long teamId);

    @Select("SELECT COUNT(*) FROM announcement WHERE team_id = #{teamId}")
    int countAnnouncements(@Param("teamId") Long teamId);
}
