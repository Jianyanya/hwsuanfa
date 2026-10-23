package com.hwsuanfa.algo.spider;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 爬虫注册表 —— 对应参考架构里的 spider.Register()。
 *
 * Spring 会把所有 SubmitLogFetcher 的实现注入进来，构造时自动登记；
 * 新增平台 = 新增一个实现类，这里零改动。
 */
@Component
public class SpiderRegistry {

    private final Map<String, SubmitLogFetcher> fetchers = new HashMap<>();

    public SpiderRegistry(List<SubmitLogFetcher> impls) {
        for (SubmitLogFetcher f : impls) {
            fetchers.put(f.platform().toUpperCase(), f);
        }
    }

    /** 未接入的平台返回 null（由 UseCase 报「需要登录 Cookie / 未接入」） */
    public SubmitLogFetcher get(String platform) {
        return fetchers.get(platform == null ? "" : platform.toUpperCase());
    }

    public boolean supports(String platform) {
        return get(platform) != null;
    }
}
