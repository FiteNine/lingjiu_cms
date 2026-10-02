package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cms_media")
public class CmsMedia {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属站点，见 cms_site.id */
    private Long siteId;

    private String name;

    /** 本地相对路径 */
    private String path;

    /** 访问 URL，如 /uploads/2026/01/xxx.png */
    private String url;

    private Long size;

    private String mimeType;

    private String ext;

    /** 无障碍/SEO 文本，为空时模板回退到内容 title（[field:cover.alt/]） */
    private String alt;

    /** 原图宽（像素），上传时由图像库读出（[field:cover.width/]） */
    private Integer width;

    /** 原图高（像素） */
    private Integer height;

    /** 派生尺寸生成状态：PENDING / DONE / FAILED */
    private String deriveStatus;

    /** LOCAL / OSS（预留） */
    private String storageType;

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
