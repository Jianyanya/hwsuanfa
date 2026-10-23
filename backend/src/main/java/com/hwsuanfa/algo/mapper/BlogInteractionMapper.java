package com.hwsuanfa.algo.mapper;

import com.hwsuanfa.algo.vo.ArticleEngagement;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface BlogInteractionMapper {

    @Insert("INSERT INTO article_like (article_id, user_id) VALUES (#{articleId}, #{userId}) ON DUPLICATE KEY UPDATE id = id")
    int addLike(@Param("articleId") Long articleId, @Param("userId") Long userId);

    @Delete("DELETE FROM article_like WHERE article_id = #{articleId} AND user_id = #{userId}")
    int removeLike(@Param("articleId") Long articleId, @Param("userId") Long userId);

    @Insert("INSERT INTO article_favorite (article_id, user_id) VALUES (#{articleId}, #{userId}) ON DUPLICATE KEY UPDATE id = id")
    int addFavorite(@Param("articleId") Long articleId, @Param("userId") Long userId);

    @Delete("DELETE FROM article_favorite WHERE article_id = #{articleId} AND user_id = #{userId}")
    int removeFavorite(@Param("articleId") Long articleId, @Param("userId") Long userId);

    @Select("""
            <script>
            SELECT a.id AS article_id,
                (SELECT COUNT(*) FROM article_like l WHERE l.article_id = a.id) AS like_count,
                (SELECT COUNT(*) FROM article_favorite f WHERE f.article_id = a.id) AS favorite_count,
                (SELECT COUNT(*) FROM article_comment c WHERE c.article_id = a.id) AS comment_count,
                EXISTS(SELECT 1 FROM article_like l WHERE l.article_id = a.id AND l.user_id = #{userId}) AS liked,
                EXISTS(SELECT 1 FROM article_favorite f WHERE f.article_id = a.id AND f.user_id = #{userId}) AS favorited
            FROM article a WHERE a.id IN
            <foreach collection="articleIds" item="articleId" open="(" separator="," close=")">
                #{articleId}
            </foreach>
            </script>
            """)
    List<ArticleEngagement> selectEngagements(@Param("articleIds") Collection<Long> articleIds,
                                            @Param("userId") Long userId);
}
