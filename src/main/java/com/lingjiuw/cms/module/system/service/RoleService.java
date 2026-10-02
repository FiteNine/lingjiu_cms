package com.lingjiuw.cms.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.system.dto.RoleBrief;
import com.lingjiuw.cms.module.system.dto.RoleSaveRequest;
import com.lingjiuw.cms.module.system.entity.SysRole;
import com.lingjiuw.cms.module.system.mapper.SysRoleMapper;
import com.lingjiuw.cms.module.system.mapper.SysRoleMenuMapper;
import com.lingjiuw.cms.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public PageResult<SysRole> page(long page, long size, String name) {
        return PageResult.of(roleMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysRole>lambdaQuery()
                        .like(StringUtils.hasText(name), SysRole::getName, name)
                        .orderByAsc(SysRole::getSort)
                        .orderByDesc(SysRole::getId)));
    }

    public List<RoleBrief> all() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getStatus, 1)
                        .orderByAsc(SysRole::getSort))
                .stream()
                .map(role -> new RoleBrief(role.getId(), role.getCode(), role.getName()))
                .toList();
    }

    public void create(RoleSaveRequest request) {
        checkCodeUnique(request.code(), null);
        SysRole role = new SysRole();
        applyRequest(role, request);
        roleMapper.insert(role);
    }

    public void update(Long id, RoleSaveRequest request) {
        SysRole role = requireRole(id);
        checkCodeUnique(request.code(), id);
        applyRequest(role, request);
        roleMapper.updateById(role);
    }

    @Transactional
    public void delete(Long id) {
        requireRole(id);
        if (userRoleMapper.countByRoleId(id) > 0) {
            throw new BizException("该角色已分配用户，不能删除");
        }
        roleMapper.deleteById(id);
        roleMenuMapper.deleteByRoleId(id);
        userRoleMapper.deleteByRoleId(id);
    }

    public List<Long> menuIds(Long roleId) {
        return roleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    @Transactional
    public void assignMenus(Long roleId, List<Long> menuIds) {
        requireRole(roleId);
        roleMenuMapper.deleteByRoleId(roleId);
        if (menuIds != null && !menuIds.isEmpty()) {
            // sys_role_menu 主键为 (role_id, menu_id)，重复的 menuId 会直接触发唯一约束错误
            roleMenuMapper.insertBatch(roleId, menuIds.stream().distinct().toList());
        }
    }

    private void applyRequest(SysRole role, RoleSaveRequest request) {
        role.setCode(request.code());
        role.setName(request.name());
        role.setSort(request.sort() == null ? 0 : request.sort());
        role.setStatus(request.status() == null ? 1 : request.status());
        role.setRemark(request.remark());
    }

    private void checkCodeUnique(String code, Long excludeId) {
        Long count = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getCode, code)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("角色编码已存在");
        }
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在或已被删除");
        }
        return role;
    }
}
