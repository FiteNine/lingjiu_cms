package com.lingjiuw.cms.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.security.JwtService;
import com.lingjiuw.cms.common.security.LoginUser;
import com.lingjiuw.cms.common.security.SecurityUtils;
import com.lingjiuw.cms.module.system.dto.ChangePasswordRequest;
import com.lingjiuw.cms.module.system.dto.LoginRequest;
import com.lingjiuw.cms.module.system.dto.LoginResponse;
import com.lingjiuw.cms.module.system.dto.ProfileVO;
import com.lingjiuw.cms.module.system.entity.SysRole;
import com.lingjiuw.cms.module.system.entity.SysUser;
import com.lingjiuw.cms.module.system.mapper.SysMenuMapper;
import com.lingjiuw.cms.module.system.mapper.SysRoleMapper;
import com.lingjiuw.cms.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已停用，请联系管理员");
        }

        ProfileVO profile = buildProfile(user);
        LoginUser loginUser = LoginUser.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roles(profile.roles())
                .perms(profile.perms())
                .build();
        String token = jwtService.generate(loginUser);
        return new LoginResponse(token, "Bearer", (int) (jwtService.getExpireMillis() / 1000), profile);
    }

    /** 当前登录用户信息（重新从数据库加载，保证权限实时） */
    public ProfileVO profile() {
        Long userId = SecurityUtils.userId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在或已被删除");
        }
        return buildProfile(user);
    }

    public void changePassword(ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(SecurityUtils.userId());
        if (user == null) {
            throw new BizException(401, "用户不存在或已被删除");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BizException("原密码错误");
        }
        if (request.newPassword().equals(request.oldPassword())) {
            throw new BizException("新密码不能与原密码相同");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(update);
    }

    private ProfileVO buildProfile(SysUser user) {
        List<String> roles = roleMapper.selectByUserId(user.getId()).stream().map(SysRole::getCode).toList();
        List<String> perms = menuMapper.selectPermsByUserId(user.getId());
        return new ProfileVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(),
                user.getEmail(), user.getPhone(), roles, perms);
    }
}
