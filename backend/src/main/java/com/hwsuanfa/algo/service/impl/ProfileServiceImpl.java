package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.dto.HandleBindRequest;
import com.hwsuanfa.algo.dto.ProfileUpdateRequest;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.TokenService;
import com.hwsuanfa.algo.security.PasswordPolicy;
import com.hwsuanfa.algo.service.ProfileService;
import com.hwsuanfa.algo.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * 个人资料：查看与绑定各平台 OJ 账号。
 *
 * ⚠️ 绑定**只收各平台的用户名（handle），不收密码**：
 * 榜单匹配用的是 handle（公开接口输 handle 即可），存别人的 OJ 密码是纯安全负债。
 *
 * 归属校验说明（2026-09-22 用户决定）：成员都是线下管理的学生，
 * 走信任模式，绑定直接保存用户名即可，不做归属挑战。
 * cf_handle 的唯一索引保留 —— 那是数据完整性（防一份成绩发两份），不是反作弊。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    /** 各平台 handle 的格式规则；null 表示该平台无法校验 */
    private static final java.util.regex.Pattern CF_HANDLE_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_]{3,24}$");

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public ProfileVO myProfile(Long userId) {
        return toVO(requireUser(userId));
    }

    @Override
    @Transactional
    public ProfileVO bindHandles(Long userId, HandleBindRequest req) {
        requireUser(userId);
        // ⚠️ 不能用 updateById：MyBatis-Plus 默认忽略 null 字段，
        //    「清空 = 解绑」会静默失效。必须用 UpdateWrapper 显式 set（含 null）。
        try {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getCfHandle, trimOrNull(req.getCfHandle()))
                    .set(User::getAtcoderHandle, trimOrNull(req.getAtcoderHandle()))
                    .set(User::getLuoguHandle, trimOrNull(req.getLuoguHandle()))
                    .set(User::getLeetcodeHandle, trimOrNull(req.getLeetcodeHandle()))
                    .set(User::getNowcoderHandle, trimOrNull(req.getNowcoderHandle()))
                    .set(User::getQojHandle, trimOrNull(req.getQojHandle())));
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONFLICT, "该平台账号已被其他成员绑定");
        }

        log.info("用户 {} 更新 OJ 绑定：cf={}, atcoder={}, luogu={}, nowcoder={}",
                userId, req.getCfHandle(), req.getAtcoderHandle(),
                req.getLuoguHandle(), req.getNowcoderHandle());
        return toVO(requireUser(userId));
    }

    /* ==================== 基本资料 / 账号安全 ==================== */

    @Override
    @Transactional
    public ProfileVO updateProfile(Long userId, ProfileUpdateRequest req) {
        User u = requireUser(userId);

        // 昵称唯一性（不含自己）
        if (StringUtils.hasText(req.getNickname())) {
            String nick = req.getNickname().trim();
            Long taken = userMapper.selectCount(new LambdaUpdateWrapper<User>()
                    .eq(User::getNickname, nick).ne(User::getId, userId));
            if (taken > 0) {
                throw new BizException(ErrorCode.CONFLICT, "该昵称已被使用");
            }
        }
        // 隐私档位合法性
        if (StringUtils.hasText(req.getPrivacyLevel())
                && !Set.of("PUBLIC", "PARTIAL", "PRIVATE").contains(req.getPrivacyLevel())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "隐私档位不合法");
        }

        LambdaUpdateWrapper<User> w = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        if (StringUtils.hasText(req.getNickname())) {
            w.set(User::getNickname, req.getNickname().trim());
        }
        if (req.getAvatar() != null) {
            w.set(User::getAvatar, req.getAvatar().isEmpty() ? null : req.getAvatar());
        }
        if (req.getEmail() != null) {
            w.set(User::getEmail, trimOrNull(req.getEmail()));
        }
        if (StringUtils.hasText(req.getPrivacyLevel())) {
            w.set(User::getPrivacyLevel, req.getPrivacyLevel());
        }
        if (req.getShowCode() != null) {
            w.set(User::getShowCode, req.getShowCode());
        }
        if (req.getQq() != null) w.set(User::getQq, trimOrNull(req.getQq()));
        if (req.getWechat() != null) w.set(User::getWechat, trimOrNull(req.getWechat()));
        if (req.getPhone() != null) w.set(User::getPhone, trimOrNull(req.getPhone()));
        if (w.getSqlSet() != null && !w.getSqlSet().isBlank()) {
            try {
                userMapper.update(null, w);
            } catch (DuplicateKeyException e) {
                throw new BizException(ErrorCode.CONFLICT, "该昵称已被使用");
            }
        }
        return toVO(requireUser(userId));
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User u = requireUser(userId);
        PasswordPolicy.validate(newPassword);
        if (!passwordEncoder.matches(oldPassword, u.getPassword())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "原密码不正确");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPassword, passwordEncoder.encode(newPassword)));
        tokenService.invalidateSessions(userId);
        log.info("用户 {} 修改了密码", userId);
    }

    /* ==================== 在线校验 ==================== */

    /**
     * 平台能力表（诚实声明，别让前端和用户抱幻想）：
     *   CODEFORCES — 官方公开 API，✅ 真实校验 + 榜单抓取
     *   ATCODER    — results/history JSON 公开，✅ 真实校验 + 单人成绩抓取
     *   LEETCODE   — 非官方 GraphQL，⚠️ 尽力校验（失败不阻塞保存）
     *   NOWCODER   — 无公开接口，❌ 无法校验（仅展示）
     *   LUOGU      — 用户明确排除
     */
    @Override
    public CfVerifyResult verifyHandle(String platform, String handle) {
        String h = handle == null ? "" : handle.trim();
        String p = platform == null ? "" : platform.trim().toUpperCase();
        if (h.isEmpty()) {
            return new CfVerifyResult(false, h, null, null, "请先填写用户名");
        }
        return switch (p) {
            case "CODEFORCES" -> verifyCf(h);
            case "ATCODER" -> verifyAtcoder(h);
            case "LEETCODE" -> verifyLeetcode(h);
            case "NOWCODER" -> new CfVerifyResult(false, h, null, null,
                    "牛客没有公开接口，无法自动校验；请自行核对无误后保存");
            default -> new CfVerifyResult(false, h, null, null, "该平台不支持在线校验");
        };
    }

    /** CF：官方 user.info，存在则返回 rating/段位 */
    private CfVerifyResult verifyCf(String h) {
        if (!CF_HANDLE_PATTERN.matcher(h).matches()) {
            return new CfVerifyResult(false, h, null, null, "格式不正确（3-24 位字母数字下划线）");
        }
        JsonNode user = cfUserInfo(h);
        if (user == null) {
            return new CfVerifyResult(false, h, null, null, "该用户名在 Codeforces 上不存在");
        }
        return new CfVerifyResult(true,
                user.path("handle").asText(h),
                user.has("rating") ? user.path("rating").asInt() : null,
                user.has("rank") ? user.path("rank").asText(null) : null,
                null);
    }

    /**
     * AtCoder 存在性判定有个坑：history/json 对**不存在的用户**也返回 200 空数组
     * （打比赛的人都有 history，但没打过 rated 比赛的真实用户同样是空数组，
     * 两者无法区分）。所以存在性用**用户主页**判断（不存在返回 404），
     * rating 再从 history/json 里取。
     */
    private CfVerifyResult verifyAtcoder(String h) {
        if (!h.matches("^[A-Za-z0-9_]{3,24}$")) {
            return new CfVerifyResult(false, h, null, null, "格式不正确（3-24 位字母数字下划线）");
        }
        try {
            HttpRequest profileReq = HttpRequest.newBuilder(
                            URI.create("https://atcoder.jp/users/" + h))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> profileResp =
                    http.send(profileReq, HttpResponse.BodyHandlers.ofString());
            if (profileResp.statusCode() == 404) {
                return new CfVerifyResult(false, h, null, null, "该用户名在 AtCoder 上不存在");
            }
            if (profileResp.statusCode() != 200) {
                return new CfVerifyResult(false, h, null, null,
                        "AtCoder 接口返回 " + profileResp.statusCode() + "，稍后再试");
            }

            Integer rating = null;
            HttpRequest historyReq = HttpRequest.newBuilder(
                            URI.create("https://atcoder.jp/users/" + h + "/history/json"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> historyResp =
                    http.send(historyReq, HttpResponse.BodyHandlers.ofString());
            if (historyResp.statusCode() == 200) {
                JsonNode arr = objectMapper.readTree(historyResp.body());
                if (arr.isArray()) {
                    for (JsonNode n : arr) {
                        if (n.path("NewRating").isNumber()) {
                            rating = n.path("NewRating").asInt();
                        }
                    }
                }
            }
            return new CfVerifyResult(true, h, rating, null, null);
        } catch (Exception e) {
            log.warn("AtCoder 校验失败：{}", e.getMessage());
            return new CfVerifyResult(false, h, null, null, "无法连接 AtCoder，稍后再试");
        }
    }

    /** LeetCode：非官方 GraphQL，尽力校验（失败不阻塞保存） */
    private CfVerifyResult verifyLeetcode(String h) {
        if (!h.matches("^[A-Za-z0-9_-]{2,30}$")) {
            return new CfVerifyResult(false, h, null, null, "格式不正确（字母数字_-）");
        }
        String query = "query userContestRanking($userSlug: String!){"
                + " userContestRanking(userSlug: $userSlug){ attendedContestsCount rating }"
                + " matchedUser(username: $userSlug){ username } }";
        String body;
        try {
            body = objectMapper.writeValueAsString(java.util.Map.of(
                    "query", query, "variables", java.util.Map.of("userSlug", h)));
        } catch (Exception e) {
            return new CfVerifyResult(false, h, null, null, "请求构造失败");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://leetcode.cn/graphql"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return new CfVerifyResult(false, h, null, null,
                        "LeetCode 接口返回 " + resp.statusCode() + "，请自行核对后保存");
            }
            JsonNode root = objectMapper.readTree(resp.body()).path("data");
            JsonNode matched = root.path("matchedUser");
            if (matched.isMissingNode() || matched.isNull()) {
                return new CfVerifyResult(false, h, null, null, "该用户名在 LeetCode 上不存在");
            }
            JsonNode ranking = root.path("userContestRanking");
            Integer rating = ranking.path("rating").isNumber()
                    ? (int) ranking.path("rating").asDouble() : null;
            return new CfVerifyResult(true, h, rating, null, null);
        } catch (Exception e) {
            log.warn("LeetCode 校验失败：{}", e.getMessage());
            return new CfVerifyResult(false, h, null, null,
                    "无法连接 LeetCode 校验接口，请自行核对无误后保存");
        }
    }

    /** CF user.info 的公共封装；不存在/失败返回 null */
    private JsonNode cfUserInfo(String handle) {
        String url = "https://codeforces.com/api/user.info?handles="
                + URLEncoder.encode(handle, StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            if (!"OK".equals(root.path("status").asText())) {
                return null;
            }
            return root.path("result").path(0);
        } catch (Exception e) {
            log.warn("读取 CF 资料失败：{}", e.getMessage());
            return null;
        }
    }

    private User requireUser(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return u;
    }

    private static String trimOrNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private ProfileVO toVO(User u) {
        return ProfileVO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .nickname(u.getNickname())
                .realName(u.getRealName())
                .studentNo(u.getStudentNo())
                .className(u.getClassName())
                .avatar(u.getAvatar())
                .email(u.getEmail())
                .privacyLevel(u.getPrivacyLevel())
                .showCode(Boolean.TRUE.equals(u.getShowCode()))
                .cfHandle(u.getCfHandle())
                .atcoderHandle(u.getAtcoderHandle())
                .luoguHandle(u.getLuoguHandle())
                .leetcodeHandle(u.getLeetcodeHandle())
                .nowcoderHandle(u.getNowcoderHandle())
                .qojHandle(u.getQojHandle())
                .qq(u.getQq())
                .wechat(u.getWechat())
                .phone(u.getPhone())
                .build();
    }
}
