package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 粘贴板（短链分享）—— 文本/代码片段，可设过期时间 */
@Data
@TableName("short_link")
public class ShortLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 短码（分享链接 /paste/{code}） */
    private String code;

    private String title;

    /** TEXT / CODE */
    private String contentType;

    private String content;

    private String language;

    private Long createdBy;

    private LocalDateTime expiresAt;

    private Integer viewCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
