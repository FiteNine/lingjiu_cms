package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 新增/编辑菜单项请求，字段照 {@code cms_menu_item} 的列（static-publish.md §2.4）。
 *
 * <p>{@code kind} 的合法取值与「按 kind 必填哪个字段」在 Service 里校验。
 */
public record MenuItemSaveRequest(
        @PositiveOrZero(message = "上级菜单项 id 不能为负数")
        Long parentId,
        @Size(max = 64, message = "菜单项文字最长 64 字符") String label,
        @NotBlank(message = "菜单项类型不能为空") String kind,
        Long refId,
        @Size(max = 64, message = "指向对象的标识最长 64 字符") String refCode,
        @Size(max = 500, message = "链接地址最长 500 字符") String url,
        @Size(max = 16, message = "target 最长 16 字符") String target,
        @Size(max = 32, message = "rel 最长 32 字符") String rel,
        @Min(value = 0, message = "visible 只能是 0 或 1")
        @Max(value = 1, message = "visible 只能是 0 或 1")
        Integer visible,
        Integer sort) {
}
