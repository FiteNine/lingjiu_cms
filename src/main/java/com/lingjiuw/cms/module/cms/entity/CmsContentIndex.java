package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 字段索引表：indexed=1 的自定义字段每个取值一行，见 static-publish.md §2.5。
 * 内容保存事务内维护（同事务增删）。
 */
@Data
@TableName("cms_content_index")
public class CmsContentIndex {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long siteId;

    private Long contentId;

    private String typeCode;

    private String fieldCode;

    /** ENUM_MULTI / TAGS 等多值字段的单个取值，标量字段恒为 "default" */
    private String valueKey;

    private String valueType;

    private BigDecimal numValue;

    private String strValue;

    private LocalDateTime timeValue;

    @TableLogic
    private Integer deleted;
}
