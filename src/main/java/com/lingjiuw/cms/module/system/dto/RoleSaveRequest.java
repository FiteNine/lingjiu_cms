package com.lingjiuw.cms.module.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增/编辑角色请求 */
public record RoleSaveRequest(
        @NotBlank(message = "角色编码不能为空")
        @Size(max = 64, message = "角色编码长度不能超过64") String code,
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过64") String name,
        Integer sort,
        @Min(value = 0, message = "状态只能为 0 或 1")
        @Max(value = 1, message = "状态只能为 0 或 1") Integer status,
        @Size(max = 255, message = "备注长度不能超过255") String remark) {
}
