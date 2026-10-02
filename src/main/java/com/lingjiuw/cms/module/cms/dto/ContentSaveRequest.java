package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 新增/编辑通用内容请求（static-publish.md §2.3）。
 *
 * <p>{@code data} 是自定义字段值的对象（键 = 字段 code），按 {@code cms_field} 定义校验后
 * 落进 {@code cms_content.data}。多值字段（ENUM_MULTI / TAGS / RELATION / IMAGES / FILES）
 * 传数组，JSON 字段任意形态，其余传标量。
 *
 * <p>{@code categoryIds} 的第一个是主分类（{@code dimension='primary'}）——主分类决定
 * 详情页 URL、面包屑与 canonical，所以顺序有语义（§2.3、§2.4）。
 *
 * <p>文本字段的 {@code @Size} 与 {@code cms_content} 的列长一致，让超长输入在参数绑定阶段
 * 就被拒成 400，而不是留给数据库报 "value too long"。
 */
public record ContentSaveRequest(
        @NotBlank(message = "内容类型不能为空") @Size(max = 64, message = "内容类型最长 64 字符") String typeCode,
        Long parentId,
        @Size(max = 255, message = "内容标识最长 255 字符") String slug,
        @NotBlank(message = "标题不能为空") @Size(max = 255, message = "标题最长 255 字符") String title,
        @Size(max = 500, message = "摘要最长 500 字符") String summary,
        @Size(max = 255, message = "封面图最长 255 字符") String cover,
        @Size(max = 16, message = "内容状态最长 16 字符") String status,
        Integer sort,
        Boolean top,
        Boolean recommend,
        LocalDateTime publishTime,
        Long authorId,
        Map<String, Object> data,
        String content,
        String contentFormat,
        @Size(max = 255, message = "SEO 标题最长 255 字符") String seoTitle,
        @Size(max = 500, message = "SEO 描述最长 500 字符") String seoDescription,
        @Size(max = 255, message = "SEO 关键词最长 255 字符") String seoKeywords,
        List<Long> categoryIds,
        List<Long> tagIds) {
}
