package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 新增/编辑导航菜单请求；{@code code} 在站点内唯一 */
public record MenuSaveRequest(
        @NotBlank(message = "菜单标识不能为空")
        @Pattern(regexp = "[a-z0-9_-]{1,64}", message = "菜单标识只能用小写字母、数字、下划线或短横线")
        String code,
        @NotBlank(message = "菜单名称不能为空") @Size(max = 64, message = "菜单名称最长 64 字符") String name,
        @Min(value = 0, message = "菜单状态只能是 0 或 1")
        @Max(value = 1, message = "菜单状态只能是 0 或 1")
        Integer status,
        @PositiveOrZero(message = "排序值不能为负数")
        Integer sort) {
}
