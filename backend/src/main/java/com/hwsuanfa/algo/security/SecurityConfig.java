package com.hwsuanfa.algo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwsuanfa.algo.common.ApiResult;
import com.hwsuanfa.algo.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置。
 *
 * ⚠️ 两个必须同时满足的约定：
 *   1. 无状态（STATELESS），不创建 Session，认证完全靠 JWT。
 *   2. 认证/授权失败必须返回真实 HTTP 401/403，否则前端 lib/http.ts 的
 *      401 → 跳登录逻辑不会触发（它读的是 response.status）。
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final LoginUserService loginUserService;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(loginUserService);
        provider.setPasswordEncoder(passwordEncoder());
        // 隐藏「用户不存在」与「密码错误」的差异
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authenticationProvider(authenticationProvider())

                .authorizeHttpRequests(auth -> auth
                        // 放行：认证入口、健康检查、静态资源
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/actuator/health",
                                "/error"
                        ).permitAll()
                        // 公开可读（内部会按隐私规则裁剪字段）
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .requestMatchers("/api/tools/pastes/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/tools/pastes/*", "/api/tools/links").permitAll()
                        .requestMatchers("/api/bulletin/admin").hasRole("SITE_OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/bulletin", "/api/bulletin/{id:[0-9]+}").permitAll()
                        .requestMatchers("/api/bulletin", "/api/bulletin/**").hasRole("SITE_OWNER")
                        .requestMatchers("/api/blog/mine", "/api/blog/favorites").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/blog", "/api/blog/{id:[0-9]+}", "/api/blog/{id:[0-9]+}/comments").permitAll()
                        // 站主后台整段需要站主角色
                        .requestMatchers("/api/admin/**").hasRole("SITE_OWNER")
                        // 其余全部需要登录
                        .anyRequest().authenticated())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                write(res, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) ->
                                write(res, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN)))

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 开发期允许 Vite dev server 跨域；生产由同域反代，不依赖 CORS */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    /** 以 ApiResult 结构输出失败响应，前端能统一解析 */
    private void write(HttpServletResponse res, HttpStatus status, ErrorCode code) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(objectMapper.writeValueAsString(ApiResult.fail(code)));
    }
}
