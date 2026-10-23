package com.hwsuanfa.algo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("award_certificate")
public class AwardCertificate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long awardId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private byte[] content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
