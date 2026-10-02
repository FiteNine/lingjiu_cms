package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 站点发布选项：(site_id, option_code) → value，见 static-publish.md §2.7。
 * 键的写法是「命名空间.驼峰」（{@code page.archive} / {@code publish.cron}）。§2.7 的封闭清单
 * 只用来判定取值类型与解析口径；保存时 {@code PublishOptionService.save} 对表外的键按新增处理，
 * 不在这一层拦。
 */
@Data
@TableName("cms_site_publish_option")
public class CmsSitePublishOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属站点，见 cms_site.id */
    private Long siteId;

    /** 选项键，如 page.archive / publish.cron */
    private String optionCode;

    /** 选项值；空串表示"未设置，用引擎默认" */
    private String value;

    @TableLogic
    private Integer deleted;
}
