package com.hwsuanfa.algo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hwsuanfa.algo.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    @org.apache.ibatis.annotations.Select("""
            <script>
            SELECT a.* FROM article a JOIN article_favorite f ON f.article_id = a.id
            WHERE f.user_id = #{userId} AND a.status = 'PUBLISHED'
            <if test="keyword != null and keyword != ''">
              AND (a.title LIKE CONCAT('%', #{keyword}, '%') OR a.summary LIKE CONCAT('%', #{keyword}, '%')
                   OR a.content_md LIKE CONCAT('%', #{keyword}, '%') OR a.tags LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="tag != null and tag != ''">AND a.tags LIKE CONCAT('%', #{tag}, '%')</if>
            ORDER BY f.created_at DESC, f.id DESC
            </script>
            """)
    IPage<Article> selectFavoritesPage(Page<Article> page, @org.apache.ibatis.annotations.Param("userId") Long userId,
                                       @org.apache.ibatis.annotations.Param("keyword") String keyword,
                                       @org.apache.ibatis.annotations.Param("tag") String tag);

    @Update("UPDATE article SET view_count = COALESCE(view_count, 0) + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    @Select("SELECT a.* FROM article a JOIN article_favorite f ON f.article_id = a.id WHERE f.user_id = #{userId} AND a.status = 'PUBLISHED' ORDER BY f.created_at DESC, f.id DESC")
    List<Article> selectFavoritesByUser(@Param("userId") Long userId);
}
