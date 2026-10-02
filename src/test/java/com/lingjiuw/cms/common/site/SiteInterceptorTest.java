package com.lingjiuw.cms.common.site;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.cms.service.SiteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站点拦截器的解析口径：公开接口显式传了站点标识就不能静默回落，后台请求仍按老规矩回落默认站点。
 * 站点是否存在 / 可否访问由 SiteService 决定，这里 mock 它，只验证拦截器的分支与 ThreadLocal 清理。
 */
class SiteInterceptorTest {

    private final SiteService siteService = mock(SiteService.class);
    private final SiteInterceptor interceptor = new SiteInterceptor(siteService);

    @AfterEach
    void clearContext() {
        // 站点上下文是 ThreadLocal，用例之间必须清干净，否则线程复用时站点会串
        SiteContext.clear();
    }

    @Test
    void 公开接口传了非法站点参数时报错且不写上下文() {
        BizException e = assertThrows(BizException.class, () -> preHandle(publicRequest("abc")));
        assertEquals("站点参数不合法", e.getMessage());
        // 抛异常时 ThreadLocal 必须保持干净，避免下一个复用该线程的请求拿到脏站点
        assertTrue(assertThrows(BizException.class, SiteContext::siteId).getMessage().contains("缺少站点上下文"));
        verify(siteService, never()).resolveSiteIdStrict(anyLong());
    }

    @Test
    void 公开接口站点不存在或无权访问时报错() {
        when(siteService.resolveSiteIdStrict(99L)).thenThrow(new BizException("站点不存在或无权访问"));
        BizException e = assertThrows(BizException.class, () -> preHandle(publicRequest("99")));
        assertEquals("站点不存在或无权访问", e.getMessage());
        assertTrue(assertThrows(BizException.class, SiteContext::siteId).getMessage().contains("缺少站点上下文"));
    }

    @Test
    void 公开接口合法站点写入上下文并在请求结束后清空() {
        when(siteService.resolveSiteIdStrict(2L)).thenReturn(2L);
        MockHttpServletRequest request = publicRequest("2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(2L, SiteContext.siteId().longValue());
        interceptor.afterCompletion(request, response, new Object(), null);
        assertTrue(assertThrows(BizException.class, SiteContext::siteId).getMessage().contains("缺少站点上下文"));
    }

    @Test
    void 公开接口没显式传站点时照旧回落默认站点() {
        when(siteService.resolveSiteId(null)).thenReturn(1L);
        assertTrue(preHandle(publicRequest(null)));
        assertEquals(1L, SiteContext.siteId().longValue());
        verify(siteService, never()).resolveSiteIdStrict(anyLong());
    }

    @Test
    void 后台请求站点不存在时照旧回落默认站点() {
        when(siteService.resolveSiteId(99L)).thenReturn(1L);
        MockHttpServletRequest request = adminRequest();
        request.setParameter("siteId", "99");
        assertTrue(preHandle(request));
        assertEquals(1L, SiteContext.siteId().longValue());
        verify(siteService, never()).resolveSiteIdStrict(anyLong());
    }

    @Test
    void 公开接口请求头显式提供时优先于参数() {
        // 请求头是脏值、参数合法：请求头优先，不能拿参数"兜"回去，否则调用方分不清哪个生效
        MockHttpServletRequest request = publicRequest("2");
        request.addHeader("X-Site-Id", "abc");
        BizException e = assertThrows(BizException.class, () -> preHandle(request));
        assertEquals("站点参数不合法", e.getMessage());
    }

    private boolean preHandle(MockHttpServletRequest request) {
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    private static MockHttpServletRequest publicRequest(String siteId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/articles");
        if (siteId != null) {
            request.setParameter("siteId", siteId);
        }
        return request;
    }

    private static MockHttpServletRequest adminRequest() {
        return new MockHttpServletRequest("GET", "/api/system/users");
    }
}
