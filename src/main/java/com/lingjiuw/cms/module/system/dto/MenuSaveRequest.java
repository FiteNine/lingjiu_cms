package com.lingjiuw.cms.module.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新增/编辑菜单请求 */
public record MenuSaveRequest(
        Long parentId,
        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 64, message = "菜单名称长度不能超过64") String name,
        @Size(max = 128, message = "路由地址长度不能超过128") String path,
        @Size(max = 128, message = "组件路径长度不能超过128") String component,
        @Size(max = 64, message = "图标长度不能超过64") String icon,
        @Size(max = 128, message = "权限标识长度不能超过128") String perms,
        @NotBlank(message = "菜单类型不能为空")
        @Pattern(regexp = "DIR|MENU|BUTTON", message = "菜单类型只能为 DIR/MENU/BUTTON") String type,
        Integer sort,
        @Min(value = 0, message = "显示状态只能为 0 或 1")
        @Max(value = 1, message = "显示状态只能为 0 或 1") Integer visible,
        @Min(value = 0, message = "状态只能为 0 或 1")
        @Max(value = 1, message = "状态只能为 0 或 1") Integer status,
        @Size(max = 255, message = "备注长度不能超过255") String remark) {
}
