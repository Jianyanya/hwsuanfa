package com.hwsuanfa.algo.service;

import com.hwsuanfa.algo.dto.AwardRequest;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.vo.AwardVO;
import com.hwsuanfa.algo.entity.AwardCertificate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 获奖记录：队员提交，站主审核 */
public interface AwardService {

    /** 队员提交获奖记录（进待审核） */
    AwardVO submit(LoginUser me, AwardRequest req);

    AwardVO update(Long id, LoginUser me, AwardRequest req);

    void delete(Long id, LoginUser me);

    /** 我的获奖记录（含全部审核状态） */
    List<AwardVO> mine(Long userId);

    /** 某用户公开且已审核通过的获奖记录（他人/个人页展示） */
    List<AwardVO> publicOf(Long userId);

    /** 站主按状态查看（PENDING / APPROVED / REJECTED / 全部） */
    List<AwardVO> adminList(String status);

    /** 站主审核 */
    AwardVO audit(Long id, boolean approved, String rejectReason, LoginUser operator);

    AwardCertificate uploadCertificate(Long id, MultipartFile file, LoginUser me);

    AwardCertificate getCertificate(Long id, LoginUser me);
}
