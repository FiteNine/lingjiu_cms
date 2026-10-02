package com.lingjiuw.cms.module.system.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 角色分配菜单权限请求；清空全部菜单传空数组，但不可缺省该字段 */
public record RoleMenuRequest(
        @NotNull(message = "菜单ID列表不能为空") List<Long> menuIds) {

    public List<Long> menuIds() {
        return menuIds == null ? List.of() : menuIds;
    }
}
