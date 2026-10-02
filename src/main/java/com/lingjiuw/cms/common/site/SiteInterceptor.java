package com.lingjiuw.cms.common.site;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.cms.service.SiteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 解析当前请求属于哪个站点，写入 {@link SiteContext}。
 *
 * <p>优先级：请求头 {@code X-Site-Id}（后台管理界面用）&gt; 请求参数 {@code siteId}
 * （公开内容接口 /api/public/** 用，门户按站点 id 取内容）&gt; 系统默认站点。
 *
 * <p>后台请求解析不出有效数字、站点已经不存在、或当前用户无权访问该站点，都落到默认站点
 * （默认站点也不可访问时落到第一个可访问站点）——「系统必须有一个默认站点」在这里当兜底用。
 * 好处是切到某个站点后那个站点被删掉或收回授权，后台不会整个卡住。可访问范围见
 * {@code SiteService#accessibleSiteIds}。
 *
 * <p>公开接口（/api/public/**）显式提供了站点标识时例外：解析失败或无权访问直接抛业务异常，
 * 调用方才能区分「参数被忽略」与「参数生效」；未显式提供时才回落默认站点。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-Site-Id";
    private static final String PARAM = "siteId";
    /** 公开内容接口的路径前缀：调用方在后台之外，需要能确认站点参数真的生效 */
    private static final String PUBLIC_URI_PREFIX = "/api/public/";

    private final SiteService siteService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HEADER);
        String raw = StringUtils.hasText(header) ? header : request.getParameter(PARAM);
        if (isPublicRequest(request) && StringUtils.hasText(raw)) {
            // 公开接口：显式给了站点标识就不再静默回落，解析不出或无权访问都要让调用方看到
            Long resolved = siteService.resolveSiteIdStrict(parseStrict(raw));
            // 解析成功后才写上下文：抛异常时 ThreadLocal 保持干净，正常路径由 afterCompletion 统一清理
            SiteContext.set(resolved);
            return true;
        }
        Long requested = parse(header);
        if (requested == null) {
            requested = parse(request.getParameter(PARAM));
        }
        SiteContext.set(siteService.resolveSiteId(requested));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 线程会被复用，必须清掉
        SiteContext.clear();
    }

    private static Long parse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            // 回落默认站点是有意为之，但静默回落时线上分不清"客户端没传"和"客户端传了脏值"
            log.debug("站点 id 无法解析，按未指定处理：{}", value);
            return null;
        }
    }

    /** 公开接口专用：非空白却解析不出正整数时直接报业务错，与"没传参数"区分开 */
    private static Long parseStrict(String value) {
        long id;
        try {
            id = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new BizException("站点参数不合法");
        }
        if (id <= 0) {
            // 0 和负数不可能是站点主键，同样按"无法解析为正整数"处理
            throw new BizException("站点参数不合法");
        }
        return id;
    }

    private static boolean isPublicRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(PUBLIC_URI_PREFIX);
    }
}
