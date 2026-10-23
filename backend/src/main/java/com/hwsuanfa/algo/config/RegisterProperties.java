package com.hwsuanfa.algo.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 注册邀请码配置。
 *
 * ⚠️ 安全要点：
 *   1. **绝不能下发到前端**——前端只提交用户输入的邀请码，比对一律在后端。
 *   2. 生产环境请用环境变量覆盖（APP_REGISTER_ADMIN_CODE / APP_REGISTER_MEMBER_CODE），
 *      避免把码写进仓库。
 *   3. **管理员邀请码等同于「取得全站最高权限的钥匙」**：
 *      用它注册出来的账号 role = SITE_OWNER，拥有站主管理权限。
 *      不要发给学生，也不要和普通码混用文案。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "app.register")
public class RegisterProperties {

    /** 普通邀请码 —— 注册为队员 MEMBER */
    private String memberCode = "";

    /** 管理员邀请码 —— 注册为站主 SITE_OWNER（与教练同一权限） */
    private String adminCode = "";

    /** 是否开放注册。关掉后注册接口直接拒绝（用于临时止血） */
    private boolean enabled = true;

    /**
     * 按 IP 限制的失败尝试次数与窗口，防止有人暴力猜邀请码。
     * 邀请码是短字符串，不限流等于把后台钥匙挂在门上。
     */
    private int maxAttemptsPerWindow = 10;
    private int attemptWindowMinutes = 60;

    @PostConstruct
    void validate() {
        if (memberCode == null || memberCode.isBlank()) {
            log.warn("未配置普通邀请码（app.register.member-code），成员注册将全部被拒绝");
        }
        if (adminCode == null || adminCode.isBlank()) {
            log.warn("未配置管理员邀请码（app.register.admin-code），管理员注册将不可用");
        }
        if (adminCode != null && !adminCode.isBlank() && adminCode.equals(memberCode)) {
            // 两个码相同会让「谁是管理员」失去区分度，直接拦下
            throw new IllegalStateException("管理员邀请码与普通邀请码不能相同");
        }
        if (enabled) {
            log.info("注册功能已开启（普通码长度 {}，管理员码长度 {}）",
                    memberCode == null ? 0 : memberCode.length(),
                    adminCode == null ? 0 : adminCode.length());
        }
    }
}
