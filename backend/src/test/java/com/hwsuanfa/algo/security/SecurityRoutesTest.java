package com.hwsuanfa.algo.security;

import com.hwsuanfa.algo.controller.AdminUserController;
import com.hwsuanfa.algo.controller.BulletinController;
import com.hwsuanfa.algo.controller.ProfileController;
import com.hwsuanfa.algo.controller.ToolController;
import com.hwsuanfa.algo.common.GlobalExceptionHandler;
import com.hwsuanfa.algo.entity.Announcement;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.AnnouncementMapper;
import com.hwsuanfa.algo.mapper.FriendlyLinkMapper;
import com.hwsuanfa.algo.mapper.ShortLinkMapper;
import com.hwsuanfa.algo.mapper.UserMapper;
import com.hwsuanfa.algo.service.AuditLogService;
import com.hwsuanfa.algo.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({BulletinController.class, ToolController.class, ProfileController.class, AdminUserController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ContextConfiguration(classes = SecurityRoutesTest.TestConfig.class)
class SecurityRoutesTest {

    @Configuration
    @Import({BulletinController.class, ToolController.class, ProfileController.class,
            AdminUserController.class, GlobalExceptionHandler.class})
    static class TestConfig {
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AnnouncementMapper announcementMapper;
    @MockBean
    private ShortLinkMapper shortLinkMapper;
    @MockBean
    private FriendlyLinkMapper friendlyLinkMapper;
    @MockBean
    private UserMapper userMapper;
    @MockBean
    private AuditLogService auditLogService;
    @MockBean
    private ProfileService profileService;
    @MockBean
    private LoginUserService loginUserService;
    @MockBean
    private JwtTokenProvider tokenProvider;
    @MockBean
    private TokenService tokenService;

    @Test
    void anonymousReadsPublicListsButCannotReachManagementOrPersonalPastes() throws Exception {
        mvc.perform(get("/api/bulletin")).andExpect(status().isOk());
        mvc.perform(get("/api/tools/links")).andExpect(status().isOk());
        mvc.perform(get("/api/bulletin/admin")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tools/pastes/mine")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/tools/pastes/abcdef12")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bulletin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"公告\",\"content\":\"正文\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tools/links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"例子\",\"url\":\"https://example.com\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(shortLinkMapper);
    }

    @Test
    void ordinaryMemberCannotManageAnnouncements() throws Exception {
        mvc.perform(get("/api/bulletin/admin").with(user(member())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/bulletin/7").with(user(member())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(announcementMapper);
    }

    @Test
    void publishedTeamAnnouncementIsNotAccessibleThroughPublicDetail() throws Exception {
        Announcement announcement = new Announcement();
        announcement.setId(7L);
        announcement.setScope("TEAM");
        announcement.setPublished(true);
        when(announcementMapper.selectById(7L)).thenReturn(announcement);
        mvc.perform(get("/api/bulletin/7")).andExpect(status().isNotFound());
        announcement.setScope("GLOBAL");
        mvc.perform(get("/api/bulletin/7")).andExpect(status().isOk());
    }

    @Test
    void profileWriteEndpointsUseAuthenticatedIdentityAndValidatePassword() throws Exception {
        mvc.perform(put("/api/profile/me").with(user(member())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"新昵称\",\"privacyLevel\":\"PRIVATE\",\"userId\":999}"))
                .andExpect(status().isOk());
        verify(profileService).updateProfile(eq(1L), any());
        mvc.perform(put("/api/profile/password").with(user(member())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk());
        verify(profileService).changePassword(1L, "old-password", "new-password");
        mvc.perform(put("/api/profile/password").with(user(member())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"old-password\",\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    private LoginUser member() {
        User user = new User();
        user.setId(1L);
        user.setUsername("20260001");
        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        return new LoginUser(user);
    }
}
