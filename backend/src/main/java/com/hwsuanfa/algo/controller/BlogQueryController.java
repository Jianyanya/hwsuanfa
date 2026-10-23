package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.Article;
import com.hwsuanfa.algo.mapper.ArticleMapper;
import com.hwsuanfa.algo.mapper.BlogInteractionMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.vo.ArticleEngagement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
public class BlogQueryController {

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final BlogInteractionMapper interactionMapper;

    @GetMapping("/page")
    public ApiResult<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String tag,
                                               @AuthenticationPrincipal LoginUser me) {
        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "PUBLISHED")
                .orderByDesc(Article::getPublishedAt).orderByDesc(Article::getId);
        applySearch(query, keyword, tag);
        return ApiResult.ok(result(articleMapper.selectPage(new Page<>(safePage(page), safeSize(size)), query), me));
    }

    @GetMapping("/mine/page")
    public ApiResult<Map<String, Object>> mine(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String tag,
                                               @RequestParam(required = false) String status,
                                               @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, me.getId()).orderByDesc(Article::getId);
        applySearch(query, keyword, tag);
        if ("DRAFT".equalsIgnoreCase(status) || "PUBLISHED".equalsIgnoreCase(status)) {
            query.eq(Article::getStatus, status.toUpperCase());
        }
        return ApiResult.ok(result(articleMapper.selectPage(new Page<>(safePage(page), safeSize(size)), query), me));
    }

    @GetMapping("/favorites/page")
    public ApiResult<Map<String, Object>> favorites(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String tag,
                                                    @AuthenticationPrincipal LoginUser me) {
        requireLogin(me);
        IPage<Article> result = articleMapper.selectFavoritesPage(new Page<>(safePage(page), safeSize(size)),
                me.getId(), blankToNull(keyword), blankToNull(tag));
        return ApiResult.ok(result(result, me));
    }

    private Map<String, Object> result(IPage<Article> page, LoginUser me) {
        List<Map<String, Object>> items = cards(page.getRecords(), me);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page.getCurrent());
        result.put("size", page.getSize());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("hasNext", page.getCurrent() < page.getPages());
        return result;
    }

    private List<Map<String, Object>> cards(List<Article> articles, LoginUser me) {
        if (articles.isEmpty()) return List.of();
        Map<Long, String> authors = userMapper.selectBatchIds(articles.stream().map(Article::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(com.hwsuanfa.algo.entity.User::getId,
                        u -> u.getNickname() == null || u.getNickname().isBlank() ? "未知" : u.getNickname()));
        Map<Long, ArticleEngagement> metrics = interactionMapper.selectEngagements(
                articles.stream().map(Article::getId).toList(), me == null ? null : me.getId())
                .stream().collect(Collectors.toMap(ArticleEngagement::getArticleId, Function.identity()));
        return articles.stream().map(article -> {
            ArticleEngagement metric = metrics.get(article.getId());
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", article.getId());
            card.put("authorId", article.getAuthorId());
            card.put("title", article.getTitle());
            card.put("summary", article.getSummary() == null ? "" : article.getSummary());
            card.put("tags", article.getTags() == null ? "" : article.getTags());
            card.put("author", authors.getOrDefault(article.getAuthorId(), "未知"));
            card.put("status", article.getStatus());
            card.put("viewCount", article.getViewCount() == null ? 0 : article.getViewCount());
            card.put("publishedAt", article.getPublishedAt());
            card.put("likeCount", metric == null ? 0L : metric.getLikeCount());
            card.put("favoriteCount", metric == null ? 0L : metric.getFavoriteCount());
            card.put("commentCount", metric == null ? 0L : metric.getCommentCount());
            card.put("liked", metric != null && metric.isLiked());
            card.put("favorited", metric != null && metric.isFavorited());
            return card;
        }).toList();
    }

    private void applySearch(LambdaQueryWrapper<Article> query, String keyword, String tag) {
        String key = blankToNull(keyword);
        String normalizedTag = blankToNull(tag);
        if (key != null) query.and(w -> w.like(Article::getTitle, key).or().like(Article::getSummary, key)
                .or().like(Article::getContentMd, key).or().like(Article::getTags, key));
        if (normalizedTag != null) query.like(Article::getTags, normalizedTag);
    }

    private void requireLogin(LoginUser me) {
        if (me == null) throw new BizException(ErrorCode.UNAUTHORIZED);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static long safePage(int value) { return Math.max(1, value); }
    private static long safeSize(int value) { return Math.max(1, Math.min(100, value)); }
}
