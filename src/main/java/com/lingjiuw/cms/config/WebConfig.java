package com.lingjiuw.cms.config;

import com.lingjiuw.cms.common.site.SiteInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：把本地上传目录映射为 /uploads/** 静态资源。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SiteInterceptor siteInterceptor;

    @Value("${cms.upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 空值会让下面的拼接得到 file:/，等于把整个文件系统挂到 /uploads/** 上（SecurityConfig 已放行该路径）
        if (!StringUtils.hasText(uploadDir)) {
            throw new IllegalStateException("cms.upload.dir 未配置，拒绝把文件系统根目录映射为静态资源");
        }
        String normalized = uploadDir.trim().replaceAll("[/\\\\]+$", "").replace('\\', '/');
        if (normalized.isEmpty()) {
            throw new IllegalStateException("cms.upload.dir 配置无效：" + uploadDir + "，拒绝把文件系统根目录映射为静态资源");
        }
        String location = "file:" + normalized + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    /** 后台界面与公开接口的内容都按站点取，站点在这里统一解析 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(siteInterceptor).addPathPatterns("/api/**");
    }
}
