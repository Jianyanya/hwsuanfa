package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.Article;
import com.hwsuanfa.algo.entity.ArticleComment;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.ArticleCommentMapper;
import com.hwsuanfa.algo.mapper.ArticleMapper;
import com.hwsuanfa.algo.mapper.BlogInteractionMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.vo.ArticleEngagement;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlogControllerTest {

    private ArticleMapper articleMapper;
    private UserMapper userMapper;
    private BlogInteractionMapper interactionMapper;
    private ArticleCommentMapper commentMapper;
    private BlogController controller;

    @BeforeAll
    static void initializeArticleMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Article.class);
    }

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        userMapper = mock(UserMapper.class);
        interactionMapper = mock(BlogInteractionMapper.class);
        commentMapper = mock(ArticleCommentMapper.class);
        controller = new BlogController(articleMapper, userMapper, interactionMapper, commentMapper);
    }

    @Test
    void anonymousCannotCreateOrReadPersonalLists() {
        assertCode(ErrorCode.UNAUTHORIZED, () -> controller.create(request("正文", "DRAFT"), null));
        assertCode(ErrorCode.UNAUTHORIZED, () -> controller.mine(null));
        assertCode(ErrorCode.UNAUTHORIZED, () -> controller.favorites(null));
        verifyNoInteractions(articleMapper, interactionMapper);
    }

    @Test
    void derivesSummaryAfterCollapsingWhitespaceWithoutOverflow() {
        var result = controller.create(request("A" + "\n".repeat(300) + "B", "DRAFT"), me(1L, false));
        assertEquals("A B", result.getData().getSummary());
        assertEquals("DRAFT", result.getData().getStatus());
        assertNull(result.getData().getPublishedAt());
        assertEquals(1L, result.getData().getAuthorId());
        assertEquals(0, result.getData().getViewCount());
        verify(articleMapper).insert(result.getData());
    }

    @Test
    void publishedArticleHasTimestampAndSummaryIsAtMost100Characters() {
        var result = controller.create(request("a".repeat(150), "PUBLISHED"), me(1L, false));
        assertEquals("a".repeat(100), result.getData().getSummary());
        assertNotNull(result.getData().getPublishedAt());
    }

    @Test
    void personalCardsSupportDraftTimestampAndEngagementFields() {
        Article draft = article(12L, 1L, "DRAFT");
        when(articleMapper.selectList(any())).thenReturn(List.of(draft));
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(me(1L, false).getUser()));
        var cards = controller.mine(me(1L, false)).getData();
        assertEquals(1, cards.size());
        assertEquals("DRAFT", cards.getFirst().get("status"));
        assertNull(cards.getFirst().get("publishedAt"));
        assertEquals(1L, cards.getFirst().get("authorId"));
        assertEquals(0L, cards.getFirst().get("likeCount"));
        assertEquals(false, cards.getFirst().get("favorited"));
    }

    @Test
    void draftIsHiddenFromOthersAndCannotReceiveInteractions() {
        when(articleMapper.selectById(12L)).thenReturn(article(12L, 1L, "DRAFT"));
        LoginUser other = me(2L, false);
        assertCode(ErrorCode.NOT_FOUND, () -> controller.detail(12L, null));
        assertCode(ErrorCode.NOT_FOUND, () -> controller.detail(12L, other));
        assertCode(ErrorCode.NOT_FOUND, () -> controller.comments(12L, other));
        assertCode(ErrorCode.NOT_FOUND, () -> controller.like(12L, other));
        assertCode(ErrorCode.NOT_FOUND, () -> controller.favorite(12L, other));
        assertCode(ErrorCode.NOT_FOUND, () -> controller.comment(12L, commentRequest("测试"), other));
        verifyNoInteractions(interactionMapper, commentMapper);
        verify(articleMapper, never()).incrementViewCount(anyLong());
    }

    @Test
    void authorAndOwnerCanReadDraftWithoutIncrementingViews() {
        when(articleMapper.selectById(12L)).thenReturn(article(12L, 1L, "DRAFT"));
        assertEquals("DRAFT", ((Article) controller.detail(12L, me(1L, false)).getData().get("article")).getStatus());
        assertEquals("DRAFT", ((Article) controller.detail(12L, me(3L, true)).getData().get("article")).getStatus());
        verify(articleMapper, never()).incrementViewCount(anyLong());
    }

    @Test
    void publishedDetailUsesAtomicViewIncrementAndTopLevelEngagement() {
        Article before = article(12L, 1L, "PUBLISHED");
        before.setViewCount(7);
        Article after = article(12L, 1L, "PUBLISHED");
        after.setViewCount(8);
        when(articleMapper.selectById(12L)).thenReturn(before, after);
        ArticleEngagement engagement = new ArticleEngagement();
        engagement.setArticleId(12L);
        engagement.setLikeCount(3);
        engagement.setFavoriteCount(2);
        engagement.setCommentCount(1);
        engagement.setLiked(true);
        when(interactionMapper.selectEngagements(List.of(12L), 2L)).thenReturn(List.of(engagement));
        var result = controller.detail(12L, me(2L, false)).getData();
        assertEquals(8, ((Article) result.get("article")).getViewCount());
        assertEquals(3L, result.get("likeCount"));
        assertEquals(2L, result.get("favoriteCount"));
        assertEquals(1L, result.get("commentCount"));
        assertEquals(true, result.get("liked"));
        assertFalse(result.containsKey("engagement"));
        verify(articleMapper).incrementViewCount(12L);
        verify(articleMapper, never()).updateById(any(Article.class));
    }

    @Test
    void usersCannotModifyOrDeleteAnotherAuthorsArticle() {
        when(articleMapper.selectById(12L)).thenReturn(article(12L, 1L, "PUBLISHED"));
        assertCode(ErrorCode.FORBIDDEN, () -> controller.update(12L, request("正文", "DRAFT"), me(2L, false)));
        assertCode(ErrorCode.FORBIDDEN, () -> controller.delete(12L, me(2L, false)));
        verify(articleMapper, never()).update(isNull(), any(Wrapper.class));
        verify(articleMapper, never()).deleteById(anyLong());
    }

    @Test
    void switchingToDraftExplicitlyClearsTagsAndPublicationTime() {
        Article existing = article(12L, 1L, "PUBLISHED");
        existing.setPublishedAt(LocalDateTime.now().minusDays(1));
        existing.setTags("动态规划");
        when(articleMapper.selectById(12L)).thenReturn(existing);
        var result = controller.update(12L, request("新正文", "DRAFT"), me(1L, false)).getData();
        assertNull(result.getTags());
        assertNull(result.getPublishedAt());
        assertEquals("新正文", result.getSummary());
        ArgumentCaptor<Wrapper<Article>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(articleMapper).update(isNull(), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSet().contains("tags="));
        assertTrue(wrapper.getValue().getSqlSet().contains("published_at="));
    }

    @Test
    void commentDeletionChecksArticleAssociationAndAuthor() {
        when(articleMapper.selectById(12L)).thenReturn(article(12L, 1L, "PUBLISHED"));
        ArticleComment comment = new ArticleComment();
        comment.setId(7L);
        comment.setArticleId(99L);
        comment.setUserId(2L);
        when(commentMapper.selectById(7L)).thenReturn(comment);
        assertCode(ErrorCode.NOT_FOUND, () -> controller.deleteComment(12L, 7L, me(2L, false)));
        comment.setArticleId(12L);
        assertCode(ErrorCode.FORBIDDEN, () -> controller.deleteComment(12L, 7L, me(1L, false)));
        verify(commentMapper, never()).deleteById(anyLong());
        controller.deleteComment(12L, 7L, me(2L, false));
        verify(commentMapper).deleteById(7L);
    }

    @Test
    void commentIsCreatedForAuthenticatedUserAndTrimmed() {
        when(articleMapper.selectById(12L)).thenReturn(article(12L, 1L, "PUBLISHED"));
        doAnswer(invocation -> {
            ArticleComment comment = invocation.getArgument(0);
            comment.setId(7L);
            return 1;
        }).when(commentMapper).insert(any(ArticleComment.class));
        var result = controller.comment(12L, commentRequest("  测试评论  "), me(2L, false)).getData();
        assertEquals(2L, result.get("userId"));
        assertEquals(12L, result.get("articleId"));
        assertEquals("测试评论", result.get("content"));
        assertEquals("用户2", result.get("author"));
        assertNotNull(result.get("createdAt"));
    }

    @Test
    void validatesCommentContentAndArticleStatus() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(commentRequest(" \n ")).isEmpty());
            assertTrue(validator.validate(commentRequest("字".repeat(2000))).isEmpty());
            assertFalse(validator.validate(commentRequest("字".repeat(2001))).isEmpty());
            assertFalse(validator.validate(request("正文", "INVALID")).isEmpty());
        }
    }

    private void assertCode(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BizException.class, action).getErrorCode());
    }

    private Article article(Long id, Long authorId, String status) {
        Article article = new Article();
        article.setId(id);
        article.setAuthorId(authorId);
        article.setTitle("测试文章");
        article.setContentMd("正文");
        article.setStatus(status);
        return article;
    }

    private BlogController.ArticleRequest request(String content, String status) {
        BlogController.ArticleRequest request = new BlogController.ArticleRequest();
        request.setTitle(" 测试文章 ");
        request.setContentMd(content);
        request.setStatus(status);
        return request;
    }

    private BlogController.CommentRequest commentRequest(String content) {
        BlogController.CommentRequest request = new BlogController.CommentRequest();
        request.setContent(content);
        return request;
    }

    private LoginUser me(Long id, boolean owner) {
        User user = new User();
        user.setId(id);
        user.setNickname("用户" + id);
        user.setRole(owner ? "SITE_OWNER" : "MEMBER");
        return new LoginUser(user);
    }
}
