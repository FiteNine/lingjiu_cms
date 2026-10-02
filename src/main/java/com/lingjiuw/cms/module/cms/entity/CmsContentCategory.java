package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 内容 ↔ 分类（多对多，带维度），见 static-publish.md §2.4。
 * dimension='primary' 每内容至多一行（数据库部分唯一索引兜底），它决定
 * categoryUrl、面包屑第二层与 canonical。
 */
@Data
@TableName("cms_content_category")
public class CmsContentCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contentId;

    private Long categoryId;

    /** primary 主分类 / secondary 副分类 */
    private String dimension;

    @TableLogic
    private Integer deleted;
}
