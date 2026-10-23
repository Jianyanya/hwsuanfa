package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.config.RegisterProperties;
import com.hwsuanfa.algo.dto.LoginRequest;
import com.hwsuanfa.algo.dto.RegisterRequest;
import com.hwsuanfa.algo.entity.Team;
import com.hwsuanfa.algo.entity.TeamMember;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.TeamMapper;
import com.hwsuanfa.algo.mapper.TeamMemberMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.JwtTokenProvider;
import com.hwsuanfa.algo.security.PasswordPolicy;
import com.hwsuanfa.algo.security.RegisterAttemptLimiter;
import com.hwsuanfa.algo.security.TokenService;
import com.hwsuanfa.algo.service.AuthService;
import com.hwsuanfa.algo.vo.LoginVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;
    private final RegisterProperties registerProps;
    private final RegisterAttemptLimiter registerLimiter;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;

    @Override
    @Transactional
    public LoginVO login(LoginRequest request) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (DisabledException e) {
            throw BizException.of(ErrorCode.ACCOUNT_DISABLED);
        } catch (BadCredentialsException e) {
            // 不区分「用户不存在」与「密码错误」
            throw BizException.of(ErrorCode.LOGIN_FAILED);
        }

        var principal = (com.hwsuanfa.algo.security.LoginUser) auth.getPrincipal();
        User user = principal.getUser();

        // 记录最后登录时间
        User patch = new User();
        patch.setId(user.getId());
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);
        user.setLastLoginAt(patch.getLastLoginAt());

        return issueTokens(user);
    }

    @Override
    @Transactional
    public LoginVO register(RegisterRequest request, String clientIp) {
        if (!registerProps.isEnabled()) {
            throw BizException.of(ErrorCode.REGISTER_DISABLED);
        }
        if (registerLimiter.isBlocked(clientIp)) {
            throw BizException.of(ErrorCode.REGISTER_ATTEMPT_LIMIT,
                    "尝试次数过多，请 " + registerProps.getAttemptWindowMinutes() + " 分钟后再试");
        }

        // ① 先验邀请码 —— 顺序很重要：码不对就直接拒，
        //    这样没有有效邀请码的人无法通过「学号是否已存在」来探测账号
        String role = resolveRoleByInviteCode(request.getInviteCode());
        if (role == null) {
            registerLimiter.recordFailure(clientIp);
            throw BizException.of(ErrorCode.INVITE_CODE_INVALID);
        }

        String studentNo = request.getStudentNo().trim();
        String nickname = request.getNickname().trim();
        PasswordPolicy.validate(request.getPassword());

        // ② 唯一性校验（并发场景下由唯一索引兜底，见 catch）
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, studentNo)) > 0) {
            throw BizException.of(ErrorCode.STUDENT_NO_EXISTS);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getNickname, nickname)) > 0) {
            throw BizException.of(ErrorCode.NICKNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(studentNo);           // 登录账号 = 学号
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(nickname);
        user.setRealName(request.getRealName().trim());
        user.setStudentNo(studentNo);
        user.setClassName(request.getClassName().trim());
        user.setRole(role);
        user.setPrivacyLevel("PARTIAL");        // 默认部分私密，由本人后续调整
        user.setShowCode(false);
        user.setStatus("ACTIVE");               // 邀请码由站主发放，视为已审核

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下两个请求同时通过了上面的查询检查，靠唯一索引兜底
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("uk_user_student_no") || msg.contains("uk_user_username")) {
                throw BizException.of(ErrorCode.STUDENT_NO_EXISTS);
            }
            if (msg.contains("uk_user_nickname")) {
                throw BizException.of(ErrorCode.NICKNAME_EXISTS);
            }
            throw BizException.of(ErrorCode.CONFLICT, "注册失败，请稍后重试");
        }

        registerLimiter.clear(clientIp);

        log.info("新用户注册成功：{}（{}），角色 {}，班级 {}",
                nickname, studentNo, role, user.getClassName());

        return issueTokens(user);
    }

    /**
     * 按邀请码判定角色。返回 null 表示邀请码无效。
     *
     * 用 MessageDigest.isEqual 做**常量时间比较**：普通的 String.equals 会在首个
     * 不同字符处提前返回，理论上可被计时攻击逐位猜出管理员码。
     */
    private String resolveRoleByInviteCode(String input) {
        if (input == null) {
            return null;
        }
        String code = input.trim();
        // 两个码都完整比较，不因命中管理员码就短路，避免比较次数差异泄露信息
        boolean isAdmin = constantTimeEquals(code, registerProps.getAdminCode());
        boolean isMember = constantTimeEquals(code, registerProps.getMemberCode());
        if (isAdmin) {
            return "SITE_OWNER";
        }
        if (isMember) {
            return "MEMBER";
        }
        return null;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || b.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parse(refreshToken);
        } catch (Exception e) {
            throw BizException.of(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (!tokenProvider.isRefreshToken(claims)) {
            throw BizException.of(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = tokenProvider.getUserId(claims);
        String jti = claims.getId();
        if (tokenProvider.getSessionVersion(claims) != tokenService.sessionVersion(userId)) {
            throw BizException.of(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw BizException.of(ErrorCode.ACCOUNT_DISABLED);
        }

        if (!tokenService.consumeRefresh(jti, userId)) {
            throw BizException.of(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return issueTokens(user);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        // refresh token 从白名单移除
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                Claims c = tokenProvider.parse(refreshToken);
                if (tokenProvider.isRefreshToken(c)) {
                    tokenService.revokeRefresh(c.getId());
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // 已失效的令牌无需处理
            }
        }

        // access token 进黑名单，保证立即失效而不是等自然过期
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Claims c = tokenProvider.parse(accessToken);
                if (tokenProvider.isAccessToken(c)) {
                    tokenService.blacklist(c.getId(), tokenProvider.remaining(c));
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // 同上
            }
        }
    }

    @Override
    public LoginVO.UserVO buildUserVO(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }

        boolean siteOwner = "SITE_OWNER".equals(user.getRole());

        return LoginVO.UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                // 本人的资料返回给本人没有问题
                .realName(user.getRealName())
                .studentNo(user.getStudentNo())
                .className(user.getClassName())
                .role(user.getRole())
                .siteOwner(siteOwner)
                .privacyLevel(user.getPrivacyLevel())
                .showCode(user.getShowCode())
                .lastLoginAt(user.getLastLoginAt())
                .teams(loadTeams(userId))
                .build();
    }

    /** 加载用户所属队伍，并标出哪个是自己当队长的 */
    private List<LoginVO.TeamBrief> loadTeams(Long userId) {
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, "ACTIVE"));
        if (memberships.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).toList();
        Map<Long, Team> teams = teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));

        return memberships.stream()
                .map(m -> teams.get(m.getTeamId()))
                .filter(t -> t != null)
                .map(t -> LoginVO.TeamBrief.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .leader(userId.equals(t.getLeaderUserId()))
                        .build())
                .toList();
    }

    /** 签发双令牌并把 refresh jti 写入 Redis */
    private LoginVO issueTokens(User user) {
        boolean siteOwner = "SITE_OWNER".equals(user.getRole());
        long sessionVersion = tokenService.sessionVersion(user.getId());
        String access = tokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getRole(), siteOwner, sessionVersion);
        String refresh = tokenProvider.createRefreshToken(
                user.getId(), user.getUsername(), user.getRole(), siteOwner, sessionVersion);

        Claims refreshClaims = tokenProvider.parse(refresh);
        tokenService.saveRefresh(refreshClaims.getId(), user.getId(), tokenProvider.getRefreshTtl());

        return LoginVO.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(tokenProvider.getAccessTtl().toSeconds())
                .user(buildUserVO(user.getId()))
                .build();
    }
}
