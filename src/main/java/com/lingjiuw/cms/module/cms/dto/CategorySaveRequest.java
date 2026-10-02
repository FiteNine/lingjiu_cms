package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 新增/编辑分类请求 */
public record CategorySaveRequest(
        @PositiveOrZero(message = "上级分类 id 不能为负数")
        Long parentId,
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 64, message = "分类名称最长 64 字符")
        String name,
        @Size(max = 64, message = "分类标识最长 64 字符")
        String slug,
        @Size(max = 255, message = "分类描述最长 255 字符")
        String description,
        @Size(max = 255, message = "分类封面最长 255 字符")
        String cover,
        @PositiveOrZero(message = "排序值不能为负数")
        Integer sort,
        @Min(value = 0, message = "分类状态只能是 0 或 1")
        @Max(value = 1, message = "分类状态只能是 0 或 1")
        Integer status) {
}
