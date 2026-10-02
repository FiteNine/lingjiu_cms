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
@TableName("cms_site")
public class CmsSite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private String domain;

    private String logo;

    private String description;

    private String keywords;

    private String seoDescription;

    /** 站点目录：相对 cms.site.root-dir 的路径，如 lingjiuw.cn */
    private String rootDir;

    /** 站点目录在服务器上的绝对路径，仅用于展示，不入库 */
    @TableField(exist = false)
    private String rootDirPath;


    private String icp;

    private String contactPhone;

    private String contactEmail;

    /** 站点主语言（zh-CN / en…），写进 <html lang> 与 hreflang */
    private String lang;

    /** https / http，用于拼绝对 URL（sitemap / feed / canonical） */
    private String protocol;

    /** 当前主题名；为空时用内置 _default 主题，模板目录不存在则发布报错 */
    private String theme;

    /** 站点默认图：内容 cover 为空时输出它 */
    private String defaultCover;

    /** 社交分享默认图 */
    private String ogImage;

    /** 统计脚本，模板用 [field:site.statisticsCode/] 原样输出 */
    private String statisticsCode;

    private Integer status;

    /** 1 系统默认站点：有且仅有一个，不允许删除 */
    private Integer isDefault;

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
