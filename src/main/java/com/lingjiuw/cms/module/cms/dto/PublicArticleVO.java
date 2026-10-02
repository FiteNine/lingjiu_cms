package com.lingjiuw.cms.module.cms.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDateTime;
import java.util.List;

/** 公开接口文章视图对象（门户/官网使用），content 仅详情返回；viewCount 是计数，按数值输出 */
public record PublicArticleVO(
        Long id,
        String title,
        String slug,
        String summary,
        String content,
        String contentFormat,
        String cover,
        @JsonSerialize(using = LongNumberSerializer.class) Long viewCount,
        LocalDateTime publishTime,
        CategoryBrief category,
        List<TagBrief> tags) {
}
