package com.lingjiuw.cms.module.cms.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 通用内容视图对象（static-publish.md §2.3）。
 *
 * <p>列表接口只返回摘要列（{@code content} / {@code seoKeywords} / {@code data} 之外的字段），
 * 详情接口返回 {@code data} 对象、{@code content} 正文与 SEO 字段，供编辑页回填动态字段表单。
 *
 * <p>{@code viewCount} 是计数而不是 id：覆盖全局 Long→String，按数值输出。
 */
public record ContentVO(
        Long id,
        String typeCode,
        String typeName,
        Long parentId,
        String slug,
        String title,
        String summary,
        String cover,
        String status,
        Integer sort,
        Boolean top,
        Boolean recommend,
        LocalDateTime publishTime,
        String authorName,
        @JsonSerialize(using = LongNumberSerializer.class)
        Long viewCount,
        String contentFormat,
        String content,
        Integer wordCount,
        Map<String, Object> data,
        String seoTitle,
        String seoDescription,
        String seoKeywords,
        List<Long> categoryIds,
        List<Long> tagIds,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
