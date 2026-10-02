package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 内容类型的字段定义，见 static-publish.md §2.2 */
@Data
@TableName("cms_field")
public class CmsField {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属站点，见 cms_site.id */
    private Long siteId;

    /** 所属类型，见 cms_content_type.code */
    private String typeCode;

    /** 字段名，模板里写 [field:xxx/]；六个保留名不得使用（§5.1） */
    private String code;

    /** 后台显示名 */
    private String label;

    /** TEXT / RICHTEXT / IMAGE / RELATION…，共 19 种 */
    private String fieldType;

    /** 输出格式化器（含参数），空为默认 */
    private String formatter;

    /** 是否原样输出（富文本 = 1） */
    private Integer raw;

    private Integer required;

    private String defaultValue;

    /** ENUM / ENUM_MULTI 的选项，"值:标签" 逗号分隔 */
    private String options;

    /** 是否进静态搜索索引（§7.5） */
    private Integer searchable;

    /** 是否可被 where / orderby / relate 使用（进 cms_content_index，§2.5） */
    private Integer indexed;

    /** RELATION 字段是否允许指向配对站点的内容（§11.5） */
    private Integer crossSite;

    private String help;

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
