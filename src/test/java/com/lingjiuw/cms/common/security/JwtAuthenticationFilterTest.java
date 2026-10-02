package com.lingjiuw.cms.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationFilter 行为测试：权限以数据库重建结果为准；失效用户不认证；解析异常不抛出。
 */
class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private LoginUserLoader loginUserLoader;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        loginUserLoader = mock(LoginUserLoader.class);
        filter = new JwtAuthenticationFilter(jwtService, loginUserLoader);
    }

    @AfterEach
    void tearDown() {
        // 过滤器写入的是全局 SecurityContextHolder，用例之间必须清理，避免串用例
        SecurityContextHolder.clearContext();
    }

    @Test
    void 正常token且用户有效时_权限来自数据库而不是token() throws Exception {
        LoginUser tokenUser = LoginUser.builder()
                .id(1L).username("alice").nickname("Alice")
                .roles(List.of("admin"))
                .perms(List.of("system:user:list", "token:old:perm"))
                .build();
        LoginUser dbUser = LoginUser.builder()
                .id(1L).username("alice").nickname("Alice")
                .roles(List.of("ops"))
                .perms(List.of("system:user:list"))
                .build();
        when(jwtService.parse("valid-token")).thenReturn(tokenUser);
        when(loginUserLoader.load(1L)).thenReturn(dbUser);

        doFilter("valid-token");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertSame(dbUser, authentication.getPrincipal());
        assertEquals(List.of(new SimpleGrantedAuthority("system:user:list")),
                List.copyOf(authentication.getAuthorities()));
        assertFalse(authentication.getAuthorities().stream()
                .anyMatch(authority -> "token:old:perm".equals(authority.getAuthority())));
    }

    @Test
    void 用户已删除或停用时_不写认证信息() throws Exception {
        LoginUser tokenUser = LoginUser.builder()
                .id(2L).username("bob")
                .perms(List.of("system:user:list"))
                .build();
        when(jwtService.parse("stale-token")).thenReturn(tokenUser);
        when(loginUserLoader.load(2L)).thenReturn(null);

        doFilter("stale-token");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(loginUserLoader).load(2L);
    }

    @Test
    void token已过期时_保持未认证且不抛异常() throws Exception {
        when(jwtService.parse("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertDoesNotThrow(() -> doFilter("expired-token"));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(loginUserLoader);
    }

    @Test
    void 签名错误或格式非法时_保持未认证且不抛异常() throws Exception {
        when(jwtService.parse("bad-token")).thenThrow(new JwtException("签名错误"));

        assertDoesNotThrow(() -> doFilter("bad-token"));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(loginUserLoader);
    }

    private void doFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/users");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
