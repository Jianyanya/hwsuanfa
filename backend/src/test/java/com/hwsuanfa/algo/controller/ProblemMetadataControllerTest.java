package com.hwsuanfa.algo.controller;

import com.hwsuanfa.algo.dto.ProblemMetadataImportRequest;
import com.hwsuanfa.algo.entity.Problem;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.ProblemMapper;
import com.hwsuanfa.algo.security.LoginUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemMetadataControllerTest {

    @Test
    void fillsTagsAndReplacesPlaceholderMetadata() {
        ProblemMapper mapper = mock(ProblemMapper.class);
        Problem existing = new Problem();
        existing.setId(12L);
        existing.setTitle("LUOGU P1001");
        existing.setDifficulty("入门");
        existing.setUrl("https://www.luogu.com.cn/record/123");
        when(mapper.selectOne(any())).thenReturn(existing);

        var result = new ProblemMetadataController(mapper).importMetadata("LUOGU", request(), me());
        ArgumentCaptor<Problem> patch = ArgumentCaptor.forClass(Problem.class);
        verify(mapper).updateById(patch.capture());
        assertEquals("模拟,数学", patch.getValue().getTags());
        assertEquals("A+B Problem", patch.getValue().getTitle());
        assertEquals("https://www.luogu.com.cn/problem/P1001", patch.getValue().getUrl());
        assertNull(patch.getValue().getDifficulty());
        assertEquals(1, result.getData().get("updated"));
    }

    @Test
    void preservesExistingEditedMetadata() {
        ProblemMapper mapper = mock(ProblemMapper.class);
        Problem existing = new Problem();
        existing.setId(12L);
        existing.setTitle("手动题名");
        existing.setDifficulty("简单");
        existing.setTags("模拟");
        existing.setUrl("https://www.luogu.com.cn/problem/P1001");
        when(mapper.selectOne(any())).thenReturn(existing);

        var result = new ProblemMetadataController(mapper).importMetadata("LUOGU", request(), me());
        verify(mapper, never()).updateById(any(Problem.class));
        assertEquals(0, result.getData().get("updated"));
    }

    private ProblemMetadataImportRequest request() {
        var item = new ProblemMetadataImportRequest.Item();
        item.setProblemNo("P1001");
        item.setTitle("A+B Problem");
        item.setDifficulty("入门");
        item.setTags("模拟,数学");
        item.setUrl("https://www.luogu.com.cn/problem/P1001");
        var request = new ProblemMetadataImportRequest();
        request.setRecords(List.of(item));
        return request;
    }

    private LoginUser me() {
        User user = new User();
        user.setId(1L);
        return new LoginUser(user);
    }
}
