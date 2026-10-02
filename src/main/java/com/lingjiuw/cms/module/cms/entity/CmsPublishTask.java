package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 发布任务/批次：一次发布一行，见 static-publish.md §8.3、§8.7 */
@Data
@TableName("cms_publish_task")
public class CmsPublishTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属站点，见 cms_site.id */
    private Long siteId;

    /** 批次标识，对应 release/<batchId>/ */
    private String batchId;

    /** manual / content / schedule / theme / expire */
    private String trigger;

    /** full / incremental / narrow / aggregate */
    private String mode;

    /** PENDING / RUNNING / SUCCESS / PARTIAL / FAILED / CANCELLED */
    private String status;

    private Integer total;

    private Integer done;

    private Integer failed;

    /** 前 N 条错误 */
    private String message;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @TableLogic
    private Integer deleted;
}
