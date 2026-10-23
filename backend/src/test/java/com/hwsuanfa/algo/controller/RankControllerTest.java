package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.security.LoginUser;
import com.hwsuanfa.algo.service.RankService;
import com.hwsuanfa.algo.vo.RankVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RankControllerTest {
    @Test
    void membersCannotReadOtherClassesThroughLeaderboard() {
        RankService service = mock(RankService.class);
        var own = RankVO.UserRow.builder().userId(1L).className("一班").build();
        var other = RankVO.UserRow.builder().userId(2L).className("二班").build();
        when(service.leaderboard()).thenReturn(RankVO.builder().users(List.of(own, other)).teams(List.of()).build());
        User user = new User();
        user.setId(1L);
        user.setRole("MEMBER");
        var data = new RankController(service).leaderboard(new LoginUser(user)).getData();
        assertEquals("一班", data.getUsers().getFirst().getClassName());
        assertNull(data.getUsers().getLast().getClassName());
    }
}
