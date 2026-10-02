package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 新增/编辑字段定义请求（cms_field，static-publish.md §2.2）。
 *
 * <p>五个开关列（raw / required / searchable / indexed / cross_site）沿用表里的 0/1，不做 Boolean 转换。
 * {@code options} 是 ENUM / ENUM_MULTI 的选项表，写法是 {@code 值:标签} 逗号分隔（如
 * {@code dev:应用开发,ops:运维}），由 Service 原样入库——引擎侧的解析容忍只写值的简写
 * （没有 {@code :} 时标签 = 值），所以这里不重复实现一遍语法校验。
 *
 * <p>五个开关标 {@code @NotNull}：新增与编辑共用这个 DTO，而 Service 对未传的开关按 0 覆盖写库，
 * 漏传就会把库里的既有配置静默重置（例如 searchable / indexed 被改回 0），因此要求全量提交。
 * 文本字段不受此约束——null 在它们身上是"清空"的合法语义。
 */
public record FieldSaveRequest(
        @NotBlank(message = "所属内容类型不能为空")
        @Size(max = 64, message = "所属内容类型最长 64 字符")
        String typeCode,
        @NotBlank(message = "字段名不能为空")
        @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]{0,63}",
                message = "字段名只能由字母、数字与下划线组成，并以字母或下划线开头")
        String code,
        @NotBlank(message = "字段显示名不能为空")
        @Size(max = 64, message = "字段显示名最长 64 字符")
        String label,
        @NotBlank(message = "字段类型不能为空")
        String fieldType,
        @Size(max = 255, message = "格式化器最长 255 字符")
        String formatter,
        @NotNull(message = "是否原样输出不能为空")
        @Min(value = 0, message = "是否原样输出只能是 0 或 1")
        @Max(value = 1, message = "是否原样输出只能是 0 或 1")
        Integer raw,
        @NotNull(message = "是否必填不能为空")
        @Min(value = 0, message = "是否必填只能是 0 或 1")
        @Max(value = 1, message = "是否必填只能是 0 或 1")
        Integer required,
        @Size(max = 500, message = "默认值最长 500 字符")
        String defaultValue,
        @Size(max = 1000, message = "选项最长 1000 字符")
        String options,
        @NotNull(message = "是否进搜索索引不能为空")
        @Min(value = 0, message = "是否进搜索索引只能是 0 或 1")
        @Max(value = 1, message = "是否进搜索索引只能是 0 或 1")
        Integer searchable,
        @NotNull(message = "是否进字段索引不能为空")
        @Min(value = 0, message = "是否进字段索引只能是 0 或 1")
        @Max(value = 1, message = "是否进字段索引只能是 0 或 1")
        Integer indexed,
        @NotNull(message = "是否跨站点不能为空")
        @Min(value = 0, message = "是否跨站点只能是 0 或 1")
        @Max(value = 1, message = "是否跨站点只能是 0 或 1")
        Integer crossSite,
        @Size(max = 255, message = "填写提示最长 255 字符")
        String help,
        @PositiveOrZero(message = "排序值不能为负")
        Integer sort) {
}
