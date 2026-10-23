package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.security.TokenService;
import com.hwsuanfa.algo.service.AuditLogService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminUserControllerTest {

    private UserMapper userMapper;
    private PasswordEncoder encoder;
    private TokenService tokenService;
    private AdminUserController controller;

    @BeforeAll
    static void initMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        encoder = mock(PasswordEncoder.class);
        tokenService = mock(TokenService.class);
        controller = new AdminUserController(userMapper, encoder, mock(AuditLogService.class), tokenService);
        User target = new User();
        target.setId(2L);
        target.setUsername("20260002");
        when(userMapper.selectById(2L)).thenReturn(target);
    }

    @Test
    void passwordResetInvalidatesTargetsSessionsWithoutAffectingAdmin() {
        var request = new AdminUserController.AdminUserUpdateRequest();
        request.setNewPassword("new-password");
        when(encoder.encode("new-password")).thenReturn("new-hash");
        controller.update(2L, request, admin());
        verify(tokenService).invalidateSessions(2L);
        verify(tokenService, never()).invalidateSessions(1L);
    }

    @Test
    void disablingTargetInvalidatesItsPreviousSessions() {
        var request = new AdminUserController.AdminUserUpdateRequest();
        request.setStatus("DISABLED");
        controller.update(2L, request, admin());
        verify(tokenService).invalidateSessions(2L);
        verify(tokenService, never()).invalidateSessions(1L);
    }

    @Test
    void ordinaryProfileUpdateDoesNotInvalidateAllDevices() {
        var request = new AdminUserController.AdminUserUpdateRequest();
        request.setClassName("一班");
        controller.update(2L, request, admin());
        verifyNoInteractions(tokenService);
    }

    @Test
    void resetRejectsPasswordBeyondBcryptLimitBeforeAnyChanges() {
        var request = new AdminUserController.AdminUserUpdateRequest();
        request.setNewPassword("密".repeat(25));
        BizException exception = assertThrows(BizException.class, () -> controller.update(2L, request, admin()));
        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(encoder, tokenService);
    }

    private LoginUser admin() {
        User user = new User();
        user.setId(1L);
        user.setRole("SITE_OWNER");
        return new LoginUser(user);
    }
}
