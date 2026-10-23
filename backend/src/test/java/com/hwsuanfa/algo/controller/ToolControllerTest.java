package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.ShortLink;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.FriendlyLinkMapper;
import com.hwsuanfa.algo.mapper.ShortLinkMapper;
import com.hwsuanfa.algo.security.LoginUser;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ToolControllerTest {
    private final ShortLinkMapper pastes = mock(ShortLinkMapper.class);
    private final FriendlyLinkMapper links = mock(FriendlyLinkMapper.class);
    private final ToolController controller = new ToolController(pastes, links);

    @Test
    void pasteLimitCountsUtf8Bytes() {
        var request = new ToolController.PasteRequest();
        request.setContent("中".repeat(21846));
        BizException error = assertThrows(BizException.class, () -> controller.createPaste(request, me()));
        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        verifyNoInteractions(pastes);
    }

    @Test
    void expiredPasteIsGoneAndDoesNotIncrementViews() {
        ShortLink paste = new ShortLink();
        paste.setId(12L);
        paste.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(pastes.selectOne(any())).thenReturn(paste);
        BizException error = assertThrows(BizException.class, () -> controller.viewPaste("abcdefgh"));
        assertEquals(ErrorCode.GONE, error.getErrorCode());
        verify(pastes, never()).incrementViewCount(any());
    }

    @Test
    void pasteViewsUseAtomicIncrement() {
        ShortLink before = new ShortLink();
        before.setId(12L);
        ShortLink after = new ShortLink();
        after.setId(12L);
        after.setViewCount(11);
        when(pastes.selectOne(any())).thenReturn(before);
        when(pastes.selectById(12L)).thenReturn(after);
        assertEquals(11, controller.viewPaste("abcdefgh").getData().getViewCount());
        verify(pastes).incrementViewCount(12L);
        verify(pastes, never()).updateById(any(ShortLink.class));
    }

    @Test
    void anonymousPersonalOperationsReturnUnauthorized() {
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BizException.class, () -> controller.myPastes(null)).getErrorCode());
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BizException.class, () -> controller.deletePaste("abcdefgh", null)).getErrorCode());
        verifyNoInteractions(pastes);
    }

    @Test
    void friendlyLinksRejectDangerousProtocols() {
        var request = new ToolController.LinkRequest();
        request.setName("测试");
        request.setUrl("javascript:alert(1)");
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BizException.class, () -> controller.saveLink(request, me())).getErrorCode());
        verifyNoInteractions(links);
    }

    @Test
    void pasteExpirationHasFiniteValidatedBounds() {
        var request = new ToolController.PasteRequest();
        request.setContent("正文");
        request.setExpireDays(Integer.MAX_VALUE);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(request).isEmpty());
            request.setExpireDays(0);
            assertFalse(factory.getValidator().validate(request).isEmpty());
            request.setExpireDays(null);
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }

    private LoginUser me() {
        User user = new User();
        user.setId(1L);
        user.setRole("SITE_OWNER");
        return new LoginUser(user);
    }
}
