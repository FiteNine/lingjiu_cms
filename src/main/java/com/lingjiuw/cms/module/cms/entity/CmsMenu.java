package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 导航菜单（一组导航项的容器），见 static-publish.md §2.4 */
@Data
@TableName("cms_menu")
public class CmsMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属站点，见 cms_site.id */
    private Long siteId;

    /** main / footer / friend…，站点内唯一 */
    private String code;

    private String name;

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
