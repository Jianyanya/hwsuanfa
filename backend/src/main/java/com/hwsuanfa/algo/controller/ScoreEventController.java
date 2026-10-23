package com.hwsuanfa.algo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.entity.ScoreEvent;
import com.hwsuanfa.algo.mapper.ScoreEventMapper;
import com.hwsuanfa.algo.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 我的积分明细（本人可见） */
@RestController
@RequestMapping("/api/score")
@RequiredArgsConstructor
public class ScoreEventController {

    private final com.hwsuanfa.algo.mapper.ScoreEventMapper scoreEventMapper;

    @GetMapping("/mine")
    public ApiResult<Map<String, Object>> mine(@AuthenticationPrincipal LoginUser me) {
        List<ScoreEvent> events = scoreEventMapper.selectList(
                new LambdaQueryWrapper<ScoreEvent>()
                        .eq(ScoreEvent::getUserId, me.getId())
                        .orderByDesc(ScoreEvent::getId));
        BigDecimal total = events.stream()
                .map(ScoreEvent::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ApiResult.ok(Map.of(
                "total", total,
                "events", events.stream().map(e -> Map.of(
                        "eventType", e.getEventType(),
                        "score", e.getScore(),
                        "reason", e.getReason() == null ? "" : e.getReason(),
                        "createdAt", e.getCreatedAt().toString())).toList()));
    }
}
