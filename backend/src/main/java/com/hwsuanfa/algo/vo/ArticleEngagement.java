package com.hwsuanfa.algo.vo;

import lombok.Data;

@Data
public class ArticleEngagement {

    private Long articleId;

    private long likeCount;

    private long favoriteCount;

    private long commentCount;

    private boolean liked;

    private boolean favorited;
}
