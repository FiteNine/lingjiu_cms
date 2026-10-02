package com.lingjiuw.cms.common.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.lingjiuw.cms.common.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置：分页插件 + 审计字段自动填充。
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        // 单页上限：客户端传超大 size 时按上限截断，避免大结果集/全表扫描
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
                // 只取一次，避免两次读取之间认证上下文变化导致 createBy/updateBy 不一致
                Long userId = currentUserId();
                strictInsertFill(metaObject, "createBy", Long.class, userId);
                strictInsertFill(metaObject, "updateBy", Long.class, userId);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                strictUpdateFill(metaObject, "updateBy", Long.class, currentUserId());
            }

            private Long currentUserId() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
                    return user.getId();
                }
                // 定时任务等没有请求上下文的写入本来就没有登录用户（保持 fail-safe，不抛异常），
                // 但审计字段留空要能查到原因
                log.debug("无登录用户上下文，审计字段 createBy/updateBy 留空");
                return null;
            }
        };
    }
}
