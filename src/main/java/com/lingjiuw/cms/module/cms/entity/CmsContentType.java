package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 内容类型：站点自助定义，见 static-publish.md §2.1 */
@Data
@TableName("cms_content_type")
public class CmsContentType {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属站点，见 cms_site.id */
    private Long siteId;

    /** 类型标识：article / single / product…，站点内唯一 */
    private String code;

    /** 后台显示名 */
    private String name;

    /** CONTENT / SINGLE / TREE */
    private String kind;

    /** 是否父子层级（TREE 隐含为 1） */
    private Integer hierarchical;

    /** 详情页 URL 规则，占位符见 §7.1.1 */
    private String detailUrlPattern;

    /** 该类型的列表页 URL 规则；为空表示该类型不出列表页 */
    private String listUrlPattern;

    private String detailTemplate;

    private String listTemplate;

    /** 正文分页字段 code；为空表示不拆正文 */
    private String paginateBody;

    /** 默认排序字段；为空时引擎按 publishTime desc */
    private String sortField;

    /** asc / desc */
    private String sortOrder;

    private Integer perPage;

    private String seoTitleField;

    private String seoDescField;

    /**
     * jsonb：页面类型开关与显示期选项。读取时是 JSON 文本；写入必须走 XML 里的
     * {@code options = #{options}::jsonb}——PostgreSQL 不接受 varchar 到 jsonb 的隐式赋值，
     * 所以这里显式排除在内置的 insert / update 之外（否则 insert 直接报类型错）。
     */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String options;

    private Integer status;

    private Integer sort;

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
