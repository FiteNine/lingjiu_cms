package com.lingjiuw.cms.common.security;

import com.lingjiuw.cms.module.system.entity.SysRole;
import com.lingjiuw.cms.module.system.entity.SysUser;
import com.lingjiuw.cms.module.system.mapper.SysMenuMapper;
import com.lingjiuw.cms.module.system.mapper.SysRoleMapper;
import com.lingjiuw.cms.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按用户 id 从数据库重建登录态，供已签发 token 的每次请求使用。
 *
 * <p>角色/权限一律以数据库当前值为准，不做缓存：这是「停用/删除用户、回收角色菜单权限后
 * 旧 token 立即失效」的前提；代价是每个带 token 的请求多几次查询，当前规模下可接受，
 * 将来若成为瓶颈再考虑短 TTL 缓存（但缓存会把权限延迟窗口重新引进来）。
 */
@Component
@RequiredArgsConstructor
public class LoginUserLoader {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;

    /**
     * 加载登录用户；用户不存在（含逻辑删除）或已停用（status != 1）时返回 null。
     *
     * <p>字段口径与 JwtService.parse、AuthService.buildProfile 保持一致。
     */
    public LoginUser load(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }
        List<String> roles = roleMapper.selectByUserId(userId).stream().map(SysRole::getCode).toList();
        List<String> perms = menuMapper.selectPermsByUserId(userId);
        return LoginUser.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roles(roles)
                .perms(perms)
                .build();
    }
}
