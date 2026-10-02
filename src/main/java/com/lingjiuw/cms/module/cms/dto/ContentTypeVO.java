package com.lingjiuw.cms.module.cms.dto;

import java.time.LocalDateTime;

/**
 * 内容类型视图对象（cms_content_type）。字段与表列一一对应，前端编辑表单原样回填后即可提交，
 * 所以这里不做"布尔化"加工；{@code options} 也返回 jsonb 原文，避免解析—再序列化丢掉未知键。
 */
public record ContentTypeVO(
        Long id,
        String code,
        String name,
        String kind,
        Integer hierarchical,
        String detailUrlPattern,
        String listUrlPattern,
        String detailTemplate,
        String listTemplate,
        String paginateBody,
        String sortField,
        String sortOrder,
        Integer perPage,
        String seoTitleField,
        String seoDescField,
        String options,
        Integer status,
        Integer sort,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    /** 下拉选项：只要类型标识与显示名（字段页、内容页选类型用）。 */
    public record Option(String code, String name) {
    }
}
