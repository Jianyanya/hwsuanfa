package com.hwsuanfa.algo.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.dto.HandleBindRequest;
import com.hwsuanfa.algo.dto.ProfileUpdateRequest;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.security.TokenService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProfileServiceImplTest {

    private UserMapper userMapper;
    private PasswordEncoder encoder;
    private TokenService tokenService;
    private ProfileServiceImpl service;

    @BeforeAll
    static void initMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        encoder = mock(PasswordEncoder.class);
        tokenService = mock(TokenService.class);
        service = new ProfileServiceImpl(userMapper, new ObjectMapper(), encoder, tokenService);
        User user = new User();
        user.setId(1L);
        user.setEmail("member@example.com");
        user.setPrivacyLevel("PRIVATE");
        user.setPassword("stored-hash");
        when(userMapper.selectById(1L)).thenReturn(user);
    }

    @Test
    void profileResponseContainsSavedEmailAndPrivacy() {
        var profile = service.myProfile(1L);
        assertEquals("member@example.com", profile.getEmail());
        assertEquals("PRIVATE", profile.getPrivacyLevel());
        assertFalse(profile.getShowCode());
    }

    @Test
    void changedPasswordInvalidatesOnlyTargetUsersSessions() {
        when(encoder.matches("old-password", "stored-hash")).thenReturn(true);
        when(encoder.encode("new-password")).thenReturn("new-hash");
        service.changePassword(1L, "old-password", "new-password");
        verify(tokenService).invalidateSessions(1L);
        verify(tokenService, never()).invalidateSessions(2L);
        verify(userMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void incorrectOldPasswordDoesNotChangePasswordOrSessions() {
        assertThrows(BizException.class, () -> service.changePassword(1L, "wrong-password", "new-password"));
        verifyNoInteractions(tokenService);
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void passwordsBeyondBcryptUtf8LimitAreRejected() {
        BizException exception = assertThrows(BizException.class,
                () -> service.changePassword(1L, "old-password", "密".repeat(25)));
        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(tokenService, encoder);
    }

    @Test
    void partialProfileEditDoesNotClearExistingContacts() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setNickname("新昵称");
        service.updateProfile(1L, request);
        ArgumentCaptor<LambdaUpdateWrapper<User>> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), wrapper.capture());
        String sqlSet = wrapper.getValue().getSqlSet();
        assertTrue(sqlSet.contains("nickname"));
        assertFalse(sqlSet.contains("qq"));
        assertFalse(sqlSet.contains("wechat"));
        assertFalse(sqlSet.contains("phone"));
    }

    @Test
    void profileEditCanEnablePublicCode() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setShowCode(true);
        service.updateProfile(1L, request);
        ArgumentCaptor<LambdaUpdateWrapper<User>> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSet().contains("show_code"));
    }

    @Test
    void emptyProfileUpdateDoesNotIssueInvalidSql() {
        service.updateProfile(1L, new ProfileUpdateRequest());
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void duplicatePlatformBindingReturnsBusinessConflict() {
        when(userMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenThrow(new DuplicateKeyException("unique handle"));
        BizException exception = assertThrows(BizException.class,
                () -> service.bindHandles(1L, new HandleBindRequest()));
        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }
}
