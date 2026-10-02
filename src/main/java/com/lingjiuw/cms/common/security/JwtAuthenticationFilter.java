package com.lingjiuw.cms.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从 Authorization: Bearer xxx 中解析用户并写入 SecurityContext。
 *
 * <p>token 只用于确认身份（用户 id）与有效期；角色/权限不使用 token 里的 claim，
 * 而是每请求经 LoginUserLoader 从数据库重建，保证停用/删除用户、回收角色菜单权限后旧 token 立即失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final LoginUserLoader loginUserLoader;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            try {
                LoginUser tokenUser = jwtService.parse(header.substring(7));
                LoginUser user = loginUserLoader.load(tokenUser.getId());
                if (user == null) {
                    // 用户已删除/已停用：不放认证信息，由 AuthenticationEntryPoint 返回 401
                    log.info("token 对应用户已删除或已停用，保持未认证状态: uri={}, userId={}",
                            request.getRequestURI(), tokenUser.getId());
                } else {
                    List<SimpleGrantedAuthority> authorities = user.getPerms() == null ? List.of()
                            : user.getPerms().stream()
                                    .filter(StringUtils::hasText)
                                    .map(SimpleGrantedAuthority::new)
                                    .toList();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ExpiredJwtException e) {
                // 过期属于会话正常生命周期，重新登录即可：debug 记录，避免刷 info 日志
                log.debug("JWT 已过期，保持未认证状态: uri={}", request.getRequestURI());
            } catch (Exception e) {
                // 签名错误/格式非法/subject 非数字等：可能是攻击尝试，info 留痕；不打印 token 内容
                log.info("JWT 校验失败，保持未认证状态: uri={}, reason={}", request.getRequestURI(), e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
