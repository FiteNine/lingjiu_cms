package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 内容：所有内容类型的统一存储，见 static-publish.md §2.3 */
@Data
@TableName("cms_content")
public class CmsContent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属站点，见 cms_site.id */
    private Long siteId;

    /** 所属类型，见 cms_content_type.code */
    private String typeCode;

    /** 层级内容的父（chapter → book）；0 为根 */
    private Long parentId;

    private String slug;

    private String title;

    private String summary;

    private String cover;

    /** DRAFT / PUBLISHED / OFFLINE */
    private String status;

    private Integer sort;

    /** 置顶 */
    private Integer top;

    /** 推荐 */
    private Integer recommend;

    private LocalDateTime publishTime;

    /** 作者指向 type_code='author' 的内容项 */
    private Long authorId;

    /** 冗余快照：作者被删除后旧内容仍显示名字 */
    private String authorName;

    /** 浏览量，由动态层累加、定时重建刷进静态页 */
    private Long viewCount;

    /** RICHTEXT / MARKDOWN，同时决定 content 入库时走清洗还是预渲染 */
    private String contentFormat;

    private String seoTitle;

    private String seoDescription;

    private String seoKeywords;

    /**
     * jsonb：自定义字段值。读取时是 JSON 文本；写入必须走 XML 里的
     * {@code data = #{data}::jsonb}——PostgreSQL 不接受 varchar 到 jsonb 的隐式赋值，
     * 所以这里显式排除在内置的 insert / update 之外（否则 insert 直接报类型错）。
     */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String data;

    private String content;

    /** MARKDOWN 的预渲染结果 */
    private String contentHtml;

    /** jsonb：正文标题树。同 {@link #data}，写入走 XML 并显式 ::jsonb。 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String contentToc;

    private Integer wordCount;

    /** 到期时间，null = 不过期 */
    private LocalDateTime expireTime;

    /** 滚动 24 小时的浏览数（日榜排序依据） */
    private Long viewCountDay;

    /** 滚动 7 天的浏览数（周榜排序依据） */
    private Long viewCountWeek;

    /** 已通过（APPROVED）评论数 */
    private Integer commentCount;

    /** 评分快照 */
    private BigDecimal ratingAvg;

    private Integer ratingCount;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
