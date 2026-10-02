package com.lingjiuw.cms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * /uploads/** 的响应安全头（H-2）。
 *
 * <p>上传文件按原样落盘、由 Spring 静态资源按扩展名推断 Content-Type：.svg 会以
 * image/svg+xml 返回，内嵌 &lt;script&gt; 在被直接打开时会在 CMS 同源下执行，可读走
 * localStorage 里的 JWT。所以能执行脚本的类型必须强制下载并加 CSP 沙箱。
 *
 * <p>光栅图片保持内联（后台媒体库与 &lt;img&gt; 预览依赖它，且图片不是脚本宿主，不加 CSP）；
 * PDF 等其余类型在后台与站点里常被直接点开查看，同样只加 nosniff，不强制下载。
 */
public class UploadResourceHeaderFilter extends OncePerRequestFilter {

    /** 浏览器直接打开就能执行脚本的扩展名；svg 是本仓库放行清单里唯一的 XML / 脚本宿主格式 */
    private static final Set<String> ACTIVE_CONTENT_EXT = Set.of("svg");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (ACTIVE_CONTENT_EXT.contains(extensionOf(request.getRequestURI()))) {
            // 附件 + 沙箱双保险：即使浏览器仍内联渲染，脚本也被禁用且不继承同源
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment");
            response.setHeader("Content-Security-Policy", "sandbox");
        }
        filterChain.doFilter(request, response);
    }

    /** 取路径最后一段的扩展名；请求 URI 不含查询串，大小写归一到小写 */
    private static String extensionOf(String uri) {
        int slash = uri.lastIndexOf('/');
        int dot = uri.lastIndexOf('.');
        return dot > slash ? uri.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
