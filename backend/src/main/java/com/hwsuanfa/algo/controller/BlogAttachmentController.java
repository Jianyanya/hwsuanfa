package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.Article;
import com.hwsuanfa.algo.entity.ArticleAttachment;
import com.hwsuanfa.algo.mapper.ArticleAttachmentMapper;
import com.hwsuanfa.algo.mapper.ArticleMapper;
import com.hwsuanfa.algo.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
public class BlogAttachmentController {

    private final ArticleMapper articleMapper;
    private final ArticleAttachmentMapper attachmentMapper;

    @GetMapping("/{articleId}/attachments")
    public ApiResult<List<Map<String, Object>>> list(@PathVariable Long articleId,
                                                     @AuthenticationPrincipal LoginUser me) {
        requireReadable(articleId, me);
        return ApiResult.ok(attachmentMapper.selectList(new LambdaQueryWrapper<ArticleAttachment>()
                        .eq(ArticleAttachment::getArticleId, articleId).orderByAsc(ArticleAttachment::getId))
                .stream().map(this::card).toList());
    }

    @PostMapping(value = "/{articleId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> upload(@PathVariable Long articleId, @RequestParam("file") MultipartFile file,
                                                 @AuthenticationPrincipal LoginUser me) {
        requireOwner(articleId, me);
        if (file == null || file.isEmpty()) throw BizException.of(ErrorCode.BAD_REQUEST, "请选择附件");
        if (file.getSize() > 10L * 1024 * 1024) throw BizException.of(ErrorCode.BAD_REQUEST, "附件不能超过 10MB");
        try {
            ArticleAttachment attachment = new ArticleAttachment();
            attachment.setArticleId(articleId);
            attachment.setFileName(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
            attachment.setContentType(file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType());
            attachment.setFileSize(file.getSize());
            attachment.setContent(file.getBytes());
            attachmentMapper.insert(attachment);
            return ApiResult.ok(card(attachment));
        } catch (java.io.IOException e) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "读取附件失败");
        }
    }

    @DeleteMapping("/{articleId}/attachments/{attachmentId}")
    public ApiResult<Void> delete(@PathVariable Long articleId, @PathVariable Long attachmentId,
                                  @AuthenticationPrincipal LoginUser me) {
        requireOwner(articleId, me);
        ArticleAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !Objects.equals(articleId, attachment.getArticleId())) {
            throw BizException.of(ErrorCode.NOT_FOUND, "附件不存在");
        }
        attachmentMapper.deleteById(attachmentId);
        return ApiResult.ok();
    }

    @GetMapping("/{articleId}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long articleId, @PathVariable Long attachmentId,
                                           @AuthenticationPrincipal LoginUser me) {
        requireReadable(articleId, me);
        ArticleAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !Objects.equals(articleId, attachment.getArticleId())) {
            return ResponseEntity.notFound().build();
        }
        MediaType type;
        try { type = MediaType.parseMediaType(attachment.getContentType()); }
        catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" +
                        URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8))
                .body(attachment.getContent());
    }

    private Article requireReadable(Long id, LoginUser me) {
        Article article = articleMapper.selectById(id);
        if (article == null || (!"PUBLISHED".equals(article.getStatus())
                && (me == null || (!Objects.equals(me.getId(), article.getAuthorId()) && !me.isSiteOwner())))) {
            throw BizException.of(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    private void requireOwner(Long id, LoginUser me) {
        Article article = requireReadable(id, me);
        if (me == null) throw BizException.of(ErrorCode.UNAUTHORIZED);
        if (!Objects.equals(me.getId(), article.getAuthorId()) && !me.isSiteOwner()) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
    }

    private Map<String, Object> card(ArticleAttachment attachment) {
        return Map.of("id", attachment.getId(), "articleId", attachment.getArticleId(),
                "fileName", attachment.getFileName(), "contentType", attachment.getContentType(),
                "fileSize", attachment.getFileSize(), "createdAt", attachment.getCreatedAt(),
                "downloadUrl", "/api/blog/" + attachment.getArticleId() + "/attachments/" + attachment.getId() + "/download");
    }
}
