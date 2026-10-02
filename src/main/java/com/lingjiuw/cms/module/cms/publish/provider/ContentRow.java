package com.lingjiuw.cms.module.cms.publish.provider;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code cms_content} 的一行（static-publish.md §2.3）。
 *
 * <p>为什么不直接用实体 {@code CmsContent}：这里的字段名是给 MyBatis 的
 * {@code map-underscore-to-camel-case} 用的——{@code select c.*} 直接映射得上，
 * XML 里不用写一堆 {@code as xxx} 别名。
 *
 * <p>{@code data} / {@code contentToc} 承载的是 **JSON 文本**（{@code jsonb} 列读出来的原样字符串），
 * 解析成结构由数据层负责（{@link Json#object} / {@link Json#parse}），且应在 {@code toRow} 之后
 * 只做一次——别把这两个字段当"已解析结构"用。
 */
@Data
public class ContentRow {

    private Long id;
    private Long siteId;
    private String typeCode;
    private Long parentId;
    private String slug;
    private String title;
    private String summary;
    private String cover;
    private String status;
    private Integer sort;
    private Integer top;
    private Integer recommend;
    private LocalDateTime publishTime;
    private Long authorId;
    private String authorName;
    private Long viewCount;
    private String contentFormat;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
    private String data;
    private String content;
    private String contentHtml;
    private String contentToc;
    private Integer wordCount;
    private LocalDateTime expireTime;
    private Long viewCountDay;
    private Long viewCountWeek;
    private Integer commentCount;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 内容是否已经发布可见（判定函数在数据层只有一处，见 {@code DbContentProvider}）。 */
    public boolean published(LocalDateTime now) {
        if (!"PUBLISHED".equals(status)) {
            return false;
        }
        if (publishTime != null && publishTime.isAfter(now)) {
            return false;
        }
        return expireTime == null || expireTime.isAfter(now);
    }

    /** 主键；null 视为 0（数据层的调用点不用到处判空）。 */
    public long idOrZero() {
        return id == null ? 0L : id;
    }

    /** 父 id；null 视为 0（0 = 根）。 */
    public long parentIdOrZero() {
        return parentId == null ? 0L : parentId;
    }
}
