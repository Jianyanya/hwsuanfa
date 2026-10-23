package com.hwsuanfa.algo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hwsuanfa.algo.entity.Assignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssignmentMapper extends BaseMapper<Assignment> {
    @Select("SELECT * FROM assignment WHERE id = #{id} FOR UPDATE")
    Assignment selectByIdForUpdate(@Param("id") Long id);
}
