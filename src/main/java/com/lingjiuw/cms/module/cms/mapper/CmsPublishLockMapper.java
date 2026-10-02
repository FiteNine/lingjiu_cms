package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsPublishLock;
import org.apache.ibatis.annotations.Mapper;

/** 发布锁：抢锁/放锁都是单行操作，BaseMapper 足够；不需要 XML。 */
@Mapper
public interface CmsPublishLockMapper extends BaseMapper<CmsPublishLock> {
}
