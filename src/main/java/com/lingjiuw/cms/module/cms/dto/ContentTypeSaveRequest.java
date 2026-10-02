package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 新增/编辑内容类型请求（cms_content_type，static-publish.md §2.1）。
 *
 * <p>状态与开关列（hierarchical / status）沿用表里的 0/1，不做 Boolean 转换：这一层的取值要与
 * 入库值一一对应，省掉一层"开关转数字"的分歧。
 *
 * <p>{@code options} 是 jsonb 列的原文（JSON 文本），由 Service 校验它能解析成 JSON 对象后写库。
 * 引擎真读它的 {@code facets} 与 {@code searchable}（§7.2、§6.1.1），写坏了要到发布期才发现。
 */
public record ContentTypeSaveRequest(
        @NotBlank(message = "类型标识不能为空")
        @Pattern(regexp = "[a-z0-9_]{1,64}", message = "类型标识只能用小写字母、数字与下划线")
        String code,
        @NotBlank(message = "类型名称不能为空")
        @Size(max = 64, message = "类型名称最长 64 字符")
        String name,
        @NotBlank(message = "类型形态不能为空")
        String kind,
        @Min(value = 0, message = "是否层级只能是 0 或 1")
        @Max(value = 1, message = "是否层级只能是 0 或 1")
        Integer hierarchical,
        @Size(max = 255, message = "详情页 URL 规则最长 255 字符")
        String detailUrlPattern,
        @Size(max = 255, message = "列表页 URL 规则最长 255 字符")
        String listUrlPattern,
        @Size(max = 255, message = "详情页模板最长 255 字符")
        String detailTemplate,
        @Size(max = 255, message = "列表页模板最长 255 字符")
        String listTemplate,
        @Size(max = 64, message = "正文分页字段最长 64 字符")
        String paginateBody,
        @Size(max = 64, message = "默认排序字段最长 64 字符")
        String sortField,
        @Size(max = 8, message = "排序方向最长 8 字符")
        String sortOrder,
        @Positive(message = "每页条数必须大于 0")
        @Max(value = 200, message = "每页条数最大 200")
        Integer perPage,
        @Size(max = 64, message = "SEO 标题字段最长 64 字符")
        String seoTitleField,
        @Size(max = 64, message = "SEO 描述字段最长 64 字符")
        String seoDescField,
        @Size(max = 4000, message = "类型选项最长 4000 字符")
        String options,
        @Min(value = 0, message = "启用状态只能是 0 或 1")
        @Max(value = 1, message = "启用状态只能是 0 或 1")
        Integer status,
        @Min(value = 0, message = "排序号不能为负数")
        Integer sort) {
}
