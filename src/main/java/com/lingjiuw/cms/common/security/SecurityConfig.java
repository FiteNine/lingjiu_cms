package com.lingjiuw.cms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.common.api.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * 安全配置：无状态 JWT 认证 + 方法级权限（@PreAuthorize）。
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 后台管理页面（SPA 路由，静态资源，鉴权由前端路由守卫 + 后端 API 共同保证）
                        // 只放行 GET：这些前缀下将来若新增接口，不会被这条宽放行规则顺带放开
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/assets/**",
                                "/login", "/dashboard", "/cms/**", "/system/**", "/ai/**", "/sites", "/sites/**").permitAll()
                        // 登录、公开内容接口、上传文件访问
                        .requestMatchers("/api/auth/login", "/api/public/**", "/uploads/**").permitAll()
                        // 接口文档
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeUnauthorized(jakarta.servlet.http.HttpServletRequest request,
                                   HttpServletResponse response,
                                   org.springframework.security.core.AuthenticationException ex) throws java.io.IOException {
        log.warn("认证失败：uri={}, reason={}", request.getRequestURI(), ex.getMessage());
        write(response, HttpServletResponse.SC_UNAUTHORIZED, Result.error(401, "未登录或登录已过期"));
    }

    private void writeForbidden(jakarta.servlet.http.HttpServletRequest request,
                                HttpServletResponse response,
                                org.springframework.security.access.AccessDeniedException ex) throws java.io.IOException {
        log.warn("鉴权拒绝：uri={}, reason={}", request.getRequestURI(), ex.getMessage());
        write(response, HttpServletResponse.SC_FORBIDDEN, Result.error(403, "无权限执行该操作"));
    }

    private void write(HttpServletResponse response, int status, Result<?> body) throws java.io.IOException {
        // 响应已提交（如 error dispatch 再次触发 EntryPoint）时再写会抛 IllegalStateException，
        // 把本该是 401/403 的结果变成 500
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
