package com.hwsuanfa.algo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hwsuanfa.algo.entity.ProblemList;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProblemListMapper extends BaseMapper<ProblemList> {
    @Select("SELECT * FROM problem_list WHERE id = #{id} FOR UPDATE")
    ProblemList selectByIdForUpdate(@Param("id") Long id);
}
