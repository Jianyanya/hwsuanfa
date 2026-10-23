package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hwsuanfa.algo.common.ApiResult;
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
import com.hwsuanfa.algo.service.NotificationService;
import com.hwsuanfa.algo.vo.ArticleEngagement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/blog")
public class BlogController {

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final BlogInteractionMapper interactionMapper;
    private final ArticleCommentMapper commentMapper;
    private final NotificationService notificationService;

    @Autowired
    public BlogController(ArticleMapper articleMapper, UserMapper userMapper,
                          BlogInteractionMapper interactionMapper, ArticleCommentMapper commentMapper,
                          NotificationService notificationService) {
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.interactionMapper = interactionMapper;
        this.commentMapper = commentMapper;
        this.notificationService = notificationService;
    }

    public BlogController(ArticleMapper articleMapper, UserMapper userMapper,
                          BlogInteractionMapper interactionMapper, ArticleCommentMapper commentMapper) {
        this(articleMapper, userMapper, interactionMapper, commentMapper, null);
    }

    @Data
    public static class ArticleRequest {
        @NotBlank(message = "标题不能为空")
        @Size(max = 200)
        private String title;

        @Size(max = 500)
        private String summary;

        @NotBlank(message = "正文不能为空")
        private String contentMd;

        @Size(max = 255)
        private String tags;

        @Pattern(regexp = "DRAFT|PUBLISHED", message = "文章状态必须为 DRAFT 或 PUBLISHED")
        private String status;
    }

    @Data
    public static class CommentRequest {
        @NotBlank(message = "评论不能为空")
        @Size(max = 2000, message = "评论不能超过 2000 字")
        private String content;
    }

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list(@AuthenticationPrincipal LoginUser me) {
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "PUBLISHED")
                .orderByDesc(Article::getPublishedAt)
                .orderByDesc(Article::getId));
        return ApiResult.ok(toCards(articles, me));
    }

    @GetMapping("/mine")
    public ApiResult<List<Map<String, Object>>> mine(@AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, me.getId())
                .orderByDesc(Article::getId));
        return ApiResult.ok(toCards(articles, me));
    }

    @GetMapping("/favorites")
    public ApiResult<List<Map<String, Object>>> favorites(@AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        return ApiResult.ok(toCards(articleMapper.selectFavoritesByUser(me.getId()), me));
    }

    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id,
                                                @AuthenticationPrincipal LoginUser me) {
        Article article = requireReadable(id, me);
        if ("PUBLISHED".equals(article.getStatus())) {
            articleMapper.incrementViewCount(id);
            article = requireReadable(id, me);
        }
        User author = userMapper.selectById(article.getAuthorId());
        Map<String, Object> result = new LinkedHashMap<>(engagement(id, me));
        result.put("article", article);
        result.put("author", authorName(author));
        return ApiResult.ok(result);
    }

    @PostMapping
    public ApiResult<Article> create(@Valid @RequestBody ArticleRequest req,
                                    @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        Article article = new Article();
        article.setAuthorId(me.getId());
        article.setViewCount(0);
        applyRequest(article, req);
        articleMapper.insert(article);
        return ApiResult.ok(article);
    }

    @PutMapping("/{id}")
    public ApiResult<Article> update(@PathVariable Long id, @Valid @RequestBody ArticleRequest req,
                                    @AuthenticationPrincipal LoginUser me) {
        Article article = requireOwned(id, me);
        applyRequest(article, req);
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, id)
                .set(Article::getTitle, article.getTitle())
                .set(Article::getSummary, article.getSummary())
                .set(Article::getContentMd, article.getContentMd())
                .set(Article::getTags, article.getTags())
                .set(Article::getStatus, article.getStatus())
                .set(Article::getPublishedAt, article.getPublishedAt())
                .set(Article::getUpdatedAt, article.getUpdatedAt()));
        return ApiResult.ok(article);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LoginUser me) {
        articleMapper.deleteById(requireOwned(id, me).getId());
        return ApiResult.ok();
    }

    @Transactional
    @PutMapping("/{id}/like")
    public ApiResult<Map<String, Object>> like(@PathVariable Long id,
                                              @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        Article article = requirePublished(id);
        interactionMapper.addLike(id, me.getId());
        notifyAuthor(article, me, "BLOG_LIKE", "博客收到新的点赞", me.getNickname() + " 点赞了你的博客《" + article.getTitle() + "》");
        return ApiResult.ok(engagement(id, me));
    }

    @Transactional
    @DeleteMapping("/{id}/like")
    public ApiResult<Map<String, Object>> unlike(@PathVariable Long id,
                                                @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        requirePublished(id);
        interactionMapper.removeLike(id, me.getId());
        return ApiResult.ok(engagement(id, me));
    }

    @Transactional
    @PutMapping("/{id}/favorite")
    public ApiResult<Map<String, Object>> favorite(@PathVariable Long id,
                                                  @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        Article article = requirePublished(id);
        interactionMapper.addFavorite(id, me.getId());
        notifyAuthor(article, me, "BLOG_FAVORITE", "博客收到新的收藏", me.getNickname() + " 收藏了你的博客《" + article.getTitle() + "》");
        return ApiResult.ok(engagement(id, me));
    }

    @Transactional
    @DeleteMapping("/{id}/favorite")
    public ApiResult<Map<String, Object>> unfavorite(@PathVariable Long id,
                                                    @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        requirePublished(id);
        interactionMapper.removeFavorite(id, me.getId());
        return ApiResult.ok(engagement(id, me));
    }

    @GetMapping("/{id}/comments")
    public ApiResult<List<Map<String, Object>>> comments(@PathVariable Long id,
                                                        @AuthenticationPrincipal LoginUser me) {
        requireReadable(id, me);
        List<ArticleComment> comments = commentMapper.selectList(new LambdaQueryWrapper<ArticleComment>()
                .eq(ArticleComment::getArticleId, id)
                .orderByAsc(ArticleComment::getCreatedAt)
                .orderByAsc(ArticleComment::getId));
        Map<Long, String> authors = authorNames(comments.stream().map(ArticleComment::getUserId).toList());
        return ApiResult.ok(comments.stream()
                .map(comment -> toComment(comment, authors.getOrDefault(comment.getUserId(), "未知")))
                .toList());
    }

    @PostMapping("/{id}/comments")
    public ApiResult<Map<String, Object>> comment(@PathVariable Long id,
                                                @Valid @RequestBody CommentRequest req,
                                                @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        Article article = requirePublished(id);
        ArticleComment comment = new ArticleComment();
        comment.setArticleId(id);
        comment.setUserId(me.getId());
        comment.setContent(req.getContent().trim());
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        notifyAuthor(article, me, "BLOG_COMMENT", "博客收到新的评论", me.getNickname() + " 评论了你的博客《" + article.getTitle() + "》");
        return ApiResult.ok(toComment(comment, me.getNickname()));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ApiResult<Void> deleteComment(@PathVariable Long id, @PathVariable Long commentId,
                                        @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        requireReadable(id, me);
        ArticleComment comment = commentMapper.selectById(commentId);
        if (comment == null || !Objects.equals(comment.getArticleId(), id)) {
            throw new BizException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        if (!Objects.equals(comment.getUserId(), me.getId()) && !me.isSiteOwner()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        commentMapper.deleteById(commentId);
        return ApiResult.ok();
    }

    private void requireLogin(LoginUser me) {
        if (me == null) throw new BizException(ErrorCode.UNAUTHORIZED);
    }

    private Article requireReadable(Long id, LoginUser me) {
        Article article = articleMapper.selectById(id);
        if (article == null || (!"PUBLISHED".equals(article.getStatus())
                && (me == null || (!Objects.equals(me.getId(), article.getAuthorId()) && !me.isSiteOwner())))) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    private Article requirePublished(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || !"PUBLISHED".equals(article.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    private void notifyAuthor(Article article, LoginUser actor, String type, String title, String content) {
        if (notificationService != null && !Objects.equals(article.getAuthorId(), actor.getId())) {
            notificationService.send(article.getAuthorId(), type, title, content, "ARTICLE", article.getId());
        }
    }

    private Article requireOwned(Long id, LoginUser me) {
        requireLogin(me);
        Article article = requireReadable(id, me);
        if (!Objects.equals(me.getId(), article.getAuthorId()) && !me.isSiteOwner()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return article;
    }

    private void applyRequest(Article article, ArticleRequest req) {
        article.setTitle(req.getTitle().trim());
        String summary = req.getSummary();
        if (summary == null || summary.isBlank()) {
            String compact = req.getContentMd().replaceAll("\\s+", " ").trim();
            summary = compact.substring(0, Math.min(100, compact.length()));
        }
        article.setSummary(summary.trim());
        article.setContentMd(req.getContentMd());
        article.setTags(req.getTags() == null || req.getTags().isBlank() ? null : req.getTags().trim());
        boolean publish = !"DRAFT".equals(req.getStatus());
        if (!publish) {
            article.setPublishedAt(null);
        } else if (article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        article.setStatus(publish ? "PUBLISHED" : "DRAFT");
    }

    private Map<Long, String> authorNames(Collection<Long> authorIds) {
        if (authorIds.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(authorIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, this::authorName));
    }

    private String authorName(User author) {
        return author == null || author.getNickname() == null || author.getNickname().isBlank()
                ? "未知" : author.getNickname();
    }

    private List<Map<String, Object>> toCards(List<Article> articles, LoginUser me) {
        if (articles.isEmpty()) return List.of();
        Map<Long, String> authors = authorNames(articles.stream().map(Article::getAuthorId).toList());
        Map<Long, ArticleEngagement> metrics = interactionMapper
                .selectEngagements(articles.stream().map(Article::getId).toList(), me == null ? null : me.getId())
                .stream().collect(Collectors.toMap(ArticleEngagement::getArticleId, Function.identity()));
        return articles.stream().map(article -> {
            Map<String, Object> card = new LinkedHashMap<>(engagementMap(metrics.get(article.getId())));
            card.put("id", article.getId());
            card.put("authorId", article.getAuthorId());
            card.put("title", article.getTitle());
            card.put("summary", article.getSummary() == null ? "" : article.getSummary());
            card.put("tags", article.getTags() == null ? "" : article.getTags());
            card.put("author", authors.getOrDefault(article.getAuthorId(), "未知"));
            card.put("status", article.getStatus());
            card.put("viewCount", article.getViewCount() == null ? 0 : article.getViewCount());
            card.put("publishedAt", article.getPublishedAt());
            return card;
        }).toList();
    }

    private Map<String, Object> engagement(Long id, LoginUser me) {
        List<ArticleEngagement> metrics = interactionMapper.selectEngagements(List.of(id), me == null ? null : me.getId());
        return engagementMap(metrics.isEmpty() ? null : metrics.getFirst());
    }

    private Map<String, Object> engagementMap(ArticleEngagement metric) {
        ArticleEngagement value = metric == null ? new ArticleEngagement() : metric;
        return Map.of("likeCount", value.getLikeCount(), "favoriteCount", value.getFavoriteCount(),
                "commentCount", value.getCommentCount(), "liked", value.isLiked(), "favorited", value.isFavorited());
    }

    private Map<String, Object> toComment(ArticleComment comment, String author) {
        return Map.of("id", comment.getId(), "articleId", comment.getArticleId(), "userId", comment.getUserId(),
                "author", author == null ? "未知" : author, "content", comment.getContent(), "createdAt", comment.getCreatedAt());
    }
}
