package com.hwsuanfa.algo.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.login")
public class LoginProperties {

    private boolean enabled = true;
    private int maxAttemptsPerWindow = 10;
    private int attemptWindowMinutes = 15;

    @PostConstruct
    public void validate() {
        if (maxAttemptsPerWindow <= 0 || attemptWindowMinutes <= 0) {
            throw new IllegalStateException("登录尝试次数和限流时间必须大于 0");
        }
    }
}
