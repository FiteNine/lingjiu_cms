package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布锁：同一站点同时只有一个实例在发布，见 static-publish.md §8.8。
 * 抢锁 = insert 一行（唯一索引冲突即"已被占用"），放锁 = 删掉这一行；
 * expire_time 用于实例崩溃后的锁回收（过期的锁视为无效）。
 *
 * <p>注意字段上的 {@code @TableLogic}：放锁实际执行的是 {@code UPDATE ... SET deleted = 1}，
 * 行还在表里。重抢能成立，靠的是唯一索引 {@code uk_cms_publish_lock_site(site_id) where deleted = 0}
 * 带 {@code deleted = 0} 谓词——索引若去掉这个条件，放锁后的 insert 会一直撞唯一冲突。
 */
@Data
@TableName("cms_publish_lock")
public class CmsPublishLock {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被锁的站点，见 cms_site.id */
    private Long siteId;

    /** 持锁实例标识，如 host:pid */
    private String holder;

    private LocalDateTime acquireTime;

    private LocalDateTime expireTime;

    @TableLogic
    private Integer deleted;
}
