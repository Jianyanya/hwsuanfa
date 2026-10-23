package com.hwsuanfa.algo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hwsuanfa.algo.entity.ShortLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShortLinkMapper extends BaseMapper<ShortLink> {

    @Update("UPDATE short_link SET view_count = COALESCE(view_count, 0) + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);
}
