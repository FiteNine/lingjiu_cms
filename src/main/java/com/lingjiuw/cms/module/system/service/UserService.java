package com.lingjiuw.cms.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.security.SecurityUtils;
import com.lingjiuw.cms.module.system.dto.RoleBrief;
import com.lingjiuw.cms.module.system.dto.UserSaveRequest;
import com.lingjiuw.cms.module.system.dto.UserVO;
import com.lingjiuw.cms.module.system.entity.SysUser;
import com.lingjiuw.cms.module.system.mapper.SysRoleMapper;
import com.lingjiuw.cms.module.system.mapper.SysUserMapper;
import com.lingjiuw.cms.module.system.mapper.SysUserRoleMapper;
import com.lingjiuw.cms.module.system.mapper.SysUserSiteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserSiteMapper userSiteMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVO> page(long page, long size, String username, Integer status) {
        IPage<SysUser> result = userMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysUser>lambdaQuery()
                        .like(StringUtils.hasText(username), SysUser::getUsername, username)
                        .eq(status != null, SysUser::getStatus, status)
                        .orderByDesc(SysUser::getId));

        List<Long> userIds = result.getRecords().stream().map(SysUser::getId).toList();
        Map<Long, List<RoleBrief>> rolesByUser = userIds.isEmpty() ? Map.of()
                : roleMapper.selectRolesByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(SysRoleMapper.UserRoleRow::getUserId,
                        Collectors.mapping(row -> new RoleBrief(row.getId(), row.getCode(), row.getName()),
                                Collectors.toList())));
        Map<Long, List<Long>> siteIdsByUser = userIds.isEmpty() ? Map.of()
                : userSiteMapper.selectByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(SysUserSiteMapper.UserSiteRow::getUserId,
                        Collectors.mapping(SysUserSiteMapper.UserSiteRow::getSiteId, Collectors.toList())));

        List<UserVO> records = result.getRecords().stream()
                .map(user -> toVO(user, rolesByUser.getOrDefault(user.getId(), List.of()),
                        siteIdsByUser.getOrDefault(user.getId(), List.of())))
                .toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public void create(UserSaveRequest request) {
        checkUsernameUnique(request.username(), null);
        if (!StringUtils.hasText(request.password())) {
            throw new BizException("密码不能为空");
        }
        SysUser user = new SysUser();
        user.setStatus(1); // 新增默认启用；请求带了 status 时由 applyRequest 覆盖
        applyRequest(user, request);
        user.setPassword(passwordEncoder.encode(request.password()));
        userMapper.insert(user);
        syncRoles(user.getId(), request.roleIds());
        syncSites(user.getId(), request.siteIds());
    }

    @Transactional
    public void update(Long id, UserSaveRequest request) {
        SysUser user = requireUser(id);
        checkUsernameUnique(request.username(), id);
        applyRequest(user, request);
        if (StringUtils.hasText(request.password())) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        userMapper.updateById(user);
        syncRoles(id, request.roleIds());
        syncSites(id, request.siteIds());
    }

    @Transactional
    public void delete(Long id) {
        if (id.equals(SecurityUtils.userId())) {
            throw new BizException("不能删除当前登录用户");
        }
        requireUser(id);
        userMapper.deleteById(id);
        userRoleMapper.deleteByUserId(id);
        userSiteMapper.deleteByUserId(id);
    }

    public void updateStatus(Long id, Integer status) {
        if (id.equals(SecurityUtils.userId()) && status != null && status == 0) {
            throw new BizException("不能停用当前登录用户");
        }
        SysUser user = requireUser(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    public void resetPassword(Long id, String password) {
        if (!StringUtils.hasText(password)) {
            throw new BizException("新密码不能为空");
        }
        SysUser user = requireUser(id);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
    }

    private void applyRequest(SysUser user, UserSaveRequest request) {
        user.setUsername(request.username());
        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRemark(request.remark());
        // 编辑时未提交 status 表示不修改，避免把已停用用户意外重新启用
        if (request.status() != null) {
            user.setStatus(request.status());
        }
    }

    private void syncRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        userRoleMapper.deleteByUserId(userId);
        if (!roleIds.isEmpty()) {
            userRoleMapper.insertBatch(userId, roleIds);
        }
    }

    private void syncSites(Long userId, List<Long> siteIds) {
        if (siteIds == null) {
            return;
        }
        userSiteMapper.deleteByUserId(userId);
        if (!siteIds.isEmpty()) {
            userSiteMapper.insertBatch(userId, siteIds);
        }
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("用户名已存在");
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在或已被删除");
        }
        return user;
    }

    private UserVO toVO(SysUser user, List<RoleBrief> roles, List<Long> siteIds) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getPhone(), user.getAvatar(), user.getStatus(), user.getRemark(), user.getCreateTime(),
                roles, roles.stream().map(RoleBrief::id).toList(), siteIds);
    }
}
