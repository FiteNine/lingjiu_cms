package com.lingjiuw.cms.module.system.service;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.security.JwtService;
import com.lingjiuw.cms.module.system.dto.LoginRequest;
import com.lingjiuw.cms.module.system.entity.SysUser;
import com.lingjiuw.cms.module.system.mapper.SysMenuMapper;
import com.lingjiuw.cms.module.system.mapper.SysRoleMapper;
import com.lingjiuw.cms.module.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败限流（{@link LoginAttemptGuard}）通过登录流程体现出来的行为：
 * 连续失败锁定、成功登录清零、锁定按「用户名 + IP」隔离。
 */
class AuthServiceLoginGuardTest {

    private static final String IP = "10.0.0.1";

    private SysUserMapper userMapper;
    private SysRoleMapper roleMapper;
    private SysMenuMapper menuMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        menuMapper = mock(SysMenuMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        // 构造参数顺序与 AuthService 字段声明顺序一致
        authService = new AuthService(userMapper, roleMapper, menuMapper, passwordEncoder, jwtService,
                new LoginAttemptGuard());
    }

    @Test
    void locksAfterConsecutiveFailures() {
        SysUser user = enabledUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            BizException e = assertThrows(BizException.class,
                    () -> authService.login(new LoginRequest("admin", "wrong"), IP));
            assertEquals("用户名或密码错误", e.getMessage());
        }

        // 锁定期内即使密码正确也被拒绝，且不再查库
        BizException locked = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("admin", "right"), IP));
        assertTrue(locked.getMessage().contains("登录失败次数过多"), locked.getMessage());
        verify(userMapper, times(5)).selectOne(any());
    }

    @Test
    void successResetsFailureCount() {
        SysUser user = enabledUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);
        when(passwordEncoder.matches("right", user.getPassword())).thenReturn(true);
        stubSuccessfulLogin();

        for (int i = 0; i < 3; i++) {
            assertThrows(BizException.class,
                    () -> authService.login(new LoginRequest("admin", "wrong"), IP));
        }
        authService.login(new LoginRequest("admin", "right"), IP);

        // 若上一次成功没有清零，3 + 4 = 7 次失败早已触发锁定
        for (int i = 0; i < 4; i++) {
            BizException e = assertThrows(BizException.class,
                    () -> authService.login(new LoginRequest("admin", "wrong"), IP));
            assertEquals("用户名或密码错误", e.getMessage());
        }
    }

    @Test
    void failureCountIsIsolatedPerClientIp() {
        SysUser user = enabledUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);
        when(passwordEncoder.matches("right", user.getPassword())).thenReturn(true);
        stubSuccessfulLogin();

        for (int i = 0; i < 5; i++) {
            assertThrows(BizException.class,
                    () -> authService.login(new LoginRequest("admin", "wrong"), IP));
        }

        // 同一用户名换一个 IP 登录不受另一个 IP 的锁定影响
        authService.login(new LoginRequest("admin", "right"), "10.0.0.2");
    }

    private void stubSuccessfulLogin() {
        when(roleMapper.selectByUserId(any())).thenReturn(List.of());
        when(menuMapper.selectPermsByUserId(any())).thenReturn(List.of());
        when(jwtService.generate(any())).thenReturn("token");
        when(jwtService.getExpireMillis()).thenReturn(3_600_000L);
    }

    private static SysUser enabledUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setNickname("管理员");
        user.setPassword("hashed");
        user.setStatus(1);
        return user;
    }
}