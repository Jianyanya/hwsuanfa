package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.dto.AwardRequest;
import com.hwsuanfa.algo.entity.Award;
import com.hwsuanfa.algo.entity.AwardCertificate;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.AwardMapper;
import com.hwsuanfa.algo.mapper.AwardCertificateMapper;
import com.hwsuanfa.algo.mapper.ScoreEventMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.AwardService;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.service.NotificationService;
import com.hwsuanfa.algo.vo.AwardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AwardServiceImpl implements AwardService {

    private final AwardMapper awardMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final com.hwsuanfa.algo.service.ScoreService scoreService;
    private final NotificationService notificationService;
    private final AwardCertificateMapper certificateMapper;
    private final ScoreEventMapper scoreEventMapper;

    public AwardServiceImpl(AwardMapper awardMapper, UserMapper userMapper,
                            AuditLogService auditLogService,
                            com.hwsuanfa.algo.service.ScoreService scoreService,
                            NotificationService notificationService,
                            AwardCertificateMapper certificateMapper,
                            ScoreEventMapper scoreEventMapper) {
        this.awardMapper = awardMapper;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
        this.scoreService = scoreService;
        this.notificationService = notificationService;
        this.certificateMapper = certificateMapper;
        this.scoreEventMapper = scoreEventMapper;
    }

    @Autowired
    public AwardServiceImpl(AwardMapper awardMapper, UserMapper userMapper,
                            AuditLogService auditLogService,
                            com.hwsuanfa.algo.service.ScoreService scoreService,
                            NotificationService notificationService) {
        this(awardMapper, userMapper, auditLogService, scoreService, notificationService, null, null);
    }

    public AwardServiceImpl(AwardMapper awardMapper, UserMapper userMapper,
                            AuditLogService auditLogService,
                            com.hwsuanfa.algo.service.ScoreService scoreService) {
        this(awardMapper, userMapper, auditLogService, scoreService, null);
    }

    @Override
    @Transactional
    public AwardVO submit(LoginUser me, AwardRequest req) {
        Award award = new Award();
        award.setUserId(me.getId());
        award.setAwardName(req.getAwardName().trim());
        award.setContestName(trimOrNull(req.getContestName()));
        award.setAwardLevel(trimOrNull(req.getAwardLevel()));
        award.setAwardedAt(req.getAwardedAt());
        award.setCertUrl(trimOrNull(req.getCertUrl()));
        award.setNote(trimOrNull(req.getNote()));
        award.setIsPublic(req.getIsPublic() == null || req.getIsPublic());
        award.setAuditStatus("PENDING");
        awardMapper.insert(award);

        auditLogService.record(me, "AWARD_SUBMIT", "AWARD", award.getId(),
                null, null, "提交获奖记录：" + award.getAwardName());
        return toVO(award);
    }

    @Override
    public List<AwardVO> mine(Long userId) {
        return awardMapper.selectList(new LambdaQueryWrapper<Award>()
                        .eq(Award::getUserId, userId)
                        .orderByDesc(Award::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public AwardVO update(Long id, LoginUser me, AwardRequest req) {
        Award award = requireEditable(id, me);
        if ("APPROVED".equals(award.getAuditStatus())) {
            reverseAwardScore(award, me.getId(), "修改获奖记录，撤销原审核积分");
        }
        award.setAwardName(req.getAwardName().trim());
        award.setContestName(trimOrNull(req.getContestName()));
        award.setAwardLevel(trimOrNull(req.getAwardLevel()));
        award.setAwardedAt(req.getAwardedAt());
        award.setCertUrl(trimOrNull(req.getCertUrl()));
        award.setNote(trimOrNull(req.getNote()));
        award.setIsPublic(req.getIsPublic() == null || req.getIsPublic());
        award.setAuditStatus("PENDING");
        award.setAuditedBy(null);
        award.setAuditedAt(null);
        award.setRejectReason(null);
        awardMapper.updateById(award);
        auditLogService.record(me, "AWARD_UPDATE", "AWARD", id, null, null, "修改获奖记录：" + award.getAwardName());
        return toVO(award);
    }

    @Override
    @Transactional
    public void delete(Long id, LoginUser me) {
        Award award = requireEditable(id, me);
        if ("APPROVED".equals(award.getAuditStatus())) {
            reverseAwardScore(award, me.getId(), "删除获奖记录，撤销原审核积分");
        }
        if (certificateMapper != null) {
            certificateMapper.delete(new LambdaQueryWrapper<AwardCertificate>().eq(AwardCertificate::getAwardId, id));
        }
        awardMapper.deleteById(id);
        auditLogService.record(me, "AWARD_DELETE", "AWARD", id, null, null, "删除获奖记录：" + award.getAwardName());
    }

    @Override
    public List<AwardVO> publicOf(Long userId) {
        return awardMapper.selectList(new LambdaQueryWrapper<Award>()
                        .eq(Award::getUserId, userId)
                        .eq(Award::getAuditStatus, "APPROVED")
                        .eq(Award::getIsPublic, true)
                        .orderByDesc(Award::getAwardedAt))
                .stream().map(this::toVO).toList();
    }

    @Override
    public List<AwardVO> adminList(String status) {
        LambdaQueryWrapper<Award> q = new LambdaQueryWrapper<Award>()
                .orderByAsc(Award::getAuditStatus)
                .orderByDesc(Award::getId);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            q.eq(Award::getAuditStatus, status.trim().toUpperCase());
        }
        return awardMapper.selectList(q).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public AwardVO audit(Long id, boolean approved, String rejectReason, LoginUser operator) {
        Award award = awardMapper.selectById(id);
        if (award == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "获奖记录不存在");
        }
        if (!"PENDING".equals(award.getAuditStatus())) {
            throw BizException.of(ErrorCode.CONFLICT, "该记录已审核过");
        }
        if (!approved && (rejectReason == null || rejectReason.isBlank())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "驳回时请填写原因");
        }

        Award patch = new Award();
        patch.setId(id);
        patch.setAuditStatus(approved ? "APPROVED" : "REJECTED");
        patch.setAuditedBy(operator.getId());
        patch.setAuditedAt(LocalDateTime.now());
        patch.setRejectReason(approved ? null : rejectReason.trim());
        int changed = awardMapper.update(null, new LambdaUpdateWrapper<Award>()
                .eq(Award::getId, id)
                .eq(Award::getAuditStatus, "PENDING")
                .set(Award::getAuditStatus, patch.getAuditStatus())
                .set(Award::getAuditedBy, patch.getAuditedBy())
                .set(Award::getAuditedAt, patch.getAuditedAt())
                .set(Award::getRejectReason, patch.getRejectReason()));
        if (changed == 0) throw BizException.of(ErrorCode.CONFLICT, "该记录已审核过");

        // 审核通过 → 按奖项等级发分（国一 30 / 国二 20 / 国三 10，省级建议档 15/10/5）
        if (approved) {
            int points = awardScore(award.getAwardLevel(), award.getAwardName());
            if (points > 0) {
                scoreService.grant(award.getUserId(), "AWARD", "",
                        java.math.BigDecimal.valueOf(points), "AWARD", award.getId(),
                        "获奖：" + award.getAwardName()
                                + (award.getAwardLevel() == null ? "" : "（" + award.getAwardLevel() + "）"),
                        "award:" + award.getId());
            }
        }

        auditLogService.record(operator, approved ? "AWARD_APPROVE" : "AWARD_REJECT",
                "AWARD", id, null, null,
                (approved ? "通过获奖记录：" : "驳回获奖记录：") + award.getAwardName());
        if (notificationService != null) {
            notificationService.send(award.getUserId(), approved ? "AWARD_APPROVED" : "AWARD_REJECTED",
                    approved ? "获奖记录审核通过" : "获奖记录被驳回",
                    approved ? "你的获奖记录《" + award.getAwardName() + "》已通过审核。"
                            : "你的获奖记录《" + award.getAwardName() + "》被驳回：" + award.getRejectReason(),
                    "AWARD", award.getId());
        }
        award.setAuditStatus(patch.getAuditStatus());
        award.setAuditedBy(patch.getAuditedBy());
        award.setAuditedAt(patch.getAuditedAt());
        award.setRejectReason(patch.getRejectReason());
        return toVO(award);
    }

    @Override
    @Transactional
    public AwardCertificate uploadCertificate(Long id, MultipartFile file, LoginUser me) {
        Award award = requireEditable(id, me);
        if (file == null || file.isEmpty()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "请选择证书文件");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "证书文件不能超过 5MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!Set.of("image/png", "image/jpeg", "image/webp", "application/pdf").contains(contentType)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "证书仅支持 PNG、JPG、WEBP 或 PDF");
        }
        if (certificateMapper == null) {
            throw BizException.of(ErrorCode.INTERNAL_ERROR, "证书存储未初始化");
        }
        try {
            AwardCertificate certificate = certificateMapper.selectOne(new LambdaQueryWrapper<AwardCertificate>()
                    .eq(AwardCertificate::getAwardId, id));
            if (certificate == null) {
                certificate = new AwardCertificate();
                certificate.setAwardId(id);
            }
            certificate.setFileName(file.getOriginalFilename() == null ? "certificate" : file.getOriginalFilename());
            certificate.setContentType(contentType);
            certificate.setFileSize(file.getSize());
            certificate.setContent(file.getBytes());
            if (certificate.getId() == null) certificateMapper.insert(certificate);
            else certificateMapper.updateById(certificate);
            return certificate;
        } catch (java.io.IOException e) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "读取证书文件失败");
        }
    }

    @Override
    public AwardCertificate getCertificate(Long id, LoginUser me) {
        Award award = awardMapper.selectById(id);
        if (award == null) throw BizException.of(ErrorCode.NOT_FOUND, "获奖记录不存在");
        boolean allowed = me != null && (Objects.equals(me.getId(), award.getUserId()) || me.isSiteOwner());
        boolean publicAllowed = "APPROVED".equals(award.getAuditStatus()) && Boolean.TRUE.equals(award.getIsPublic());
        if (!allowed && !publicAllowed) throw BizException.of(ErrorCode.NOT_FOUND, "证书不存在");
        if (certificateMapper == null) return null;
        return certificateMapper.selectOne(new LambdaQueryWrapper<AwardCertificate>().eq(AwardCertificate::getAwardId, id));
    }

    private Award requireEditable(Long id, LoginUser me) {
        if (me == null) throw BizException.of(ErrorCode.UNAUTHORIZED);
        Award award = awardMapper.selectById(id);
        if (award == null) throw BizException.of(ErrorCode.NOT_FOUND, "获奖记录不存在");
        if (!Objects.equals(award.getUserId(), me.getId()) && !me.isSiteOwner()) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
        return award;
    }

    private void reverseAwardScore(Award award, Long operatorId, String reason) {
        if (scoreEventMapper == null) return;
        var event = scoreEventMapper.selectOne(new LambdaQueryWrapper<com.hwsuanfa.algo.entity.ScoreEvent>()
                .eq(com.hwsuanfa.algo.entity.ScoreEvent::getIdempotentKey, "award:" + award.getId()));
        if (event != null && event.getReversedEventId() == null && event.getScore() != null
                && event.getScore().compareTo(java.math.BigDecimal.ZERO) > 0) {
            scoreService.reverse(event.getId(), reason, operatorId);
        }
    }

    /** 奖项等级 → 积分（关键词匹配；未匹配到不发分）。改分值只动这一张表。 */
    private static int awardScore(String level, String awardName) {
        String text = (level == null ? "" : level) + " " + (awardName == null ? "" : awardName);
        if (text.contains("国") && text.contains("一")) return 30;
        if (text.contains("国") && text.contains("二")) return 20;
        if (text.contains("国") && text.contains("三")) return 10;
        if (text.contains("省") && text.contains("一")) return 15;
        if (text.contains("省") && text.contains("二")) return 10;
        if (text.contains("省") && text.contains("三")) return 5;
        return 0;
    }

    private AwardVO toVO(Award award) {
        User user = userMapper.selectById(award.getUserId());
        return AwardVO.builder()
                .id(award.getId())
                .userId(award.getUserId())
                .userNickname(user == null ? null : user.getNickname())
                .awardName(award.getAwardName())
                .contestName(award.getContestName())
                .awardLevel(award.getAwardLevel())
                .awardedAt(award.getAwardedAt())
                .certUrl(award.getCertUrl())
                .certificateAvailable(certificateMapper != null && certificateMapper.selectCount(
                        new LambdaQueryWrapper<AwardCertificate>().eq(AwardCertificate::getAwardId, award.getId())) > 0)
                .note(award.getNote())
                .isPublic(award.getIsPublic())
                .auditStatus(award.getAuditStatus())
                .rejectReason(award.getRejectReason())
                .createdAt(award.getCreatedAt())
                .build();
    }

    private static String trimOrNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
