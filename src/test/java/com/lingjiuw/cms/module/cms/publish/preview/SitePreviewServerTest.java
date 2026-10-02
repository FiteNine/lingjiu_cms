package com.lingjiuw.cms.module.cms.publish.preview;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMediaMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsTagMapper;
import com.lingjiuw.cms.module.cms.service.SiteBootstrapService;
import com.lingjiuw.cms.module.cms.service.SiteService;
import com.lingjiuw.cms.module.system.mapper.SysUserSiteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 预览站点：独立端口上把已发布的 {@code www/} 原样吐出来。
 *
 * <p>用例的口径是"nginx 上线后浏览器看到什么，预览就要看到什么"：根绝对路径要能取到文件、
 * 目录页走 index.html、没有的页面给 404 而不是后台的 index.html，越界路径一律拒绝。
 * 用临时目录当站点目录，不碰真库（{@code SiteService} 只用到 {@code siteDir} 这一条路，
 * 它读的是 {@code cms_site.root_dir}）。
 */
@DisplayName("预览站点：只读静态出口")
class SitePreviewServerTest {

    private static final long SITE_ID = 6L;
    private static final int UNUSED_BASE_PORT = 61_000;

    @TempDir
    Path tempDir;

    private SitePreviewServer server;
    private String base;

    /**
     * 一次请求配一个客户端：预览是本地一次性访问，用例之间不共享连接池，
     * 免得上一用例关掉连接后下一个用例拿到失效连接（表现为"header parser received no bytes"）。
     */
    private HttpResponse<String> send(String path, String method, HttpClient.Redirect redirect) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path.substring(1)));
        builder.method(method, HttpRequest.BodyPublishers.noBody());
        return HttpClient.newBuilder().followRedirects(redirect).build()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 默认不跟随重定向：目录没有斜杠时的 301 要看得见才行（跟随了就只能看到它后面那一跳） */
    private HttpResponse<String> get(String path) throws Exception {
        return send(path, "GET", HttpClient.Redirect.NEVER);
    }

    @BeforeEach
    void setUp() throws IOException {
        Path www = Files.createDirectories(tempDir.resolve("demo").resolve("www"));
        Files.writeString(www.resolve("index.html"), "<h1>首页</h1>", StandardCharsets.UTF_8);
        Files.createDirectories(www.resolve("news").resolve("tech"));
        Files.writeString(www.resolve("news").resolve("tech").resolve("index.html"),
                "<h1>技术与发布</h1>", StandardCharsets.UTF_8);
        Files.writeString(www.resolve("404.html"), "<h1>没有这个页面</h1>", StandardCharsets.UTF_8);
        Files.writeString(www.resolve("feed.xml"), "<rss/>", StandardCharsets.UTF_8);
        Files.createDirectories(www.resolve("assets"));
        Files.writeString(www.resolve("assets").resolve("site.css"), "body{}", StandardCharsets.UTF_8);

        CmsSite site = new CmsSite();
        site.setId(SITE_ID);
        site.setCode("demo");
        site.setName("灵久演示站");
        site.setRootDir("demo");
        CmsSiteMapper siteMapper = mock(CmsSiteMapper.class);
        when(siteMapper.selectById(anyLong())).thenReturn(site);
        SiteService siteService = new SiteService(siteMapper,
                mock(CmsContentMapper.class), mock(CmsCategoryMapper.class), mock(CmsTagMapper.class),
                mock(CmsMediaMapper.class), mock(SysUserSiteMapper.class), mock(SiteBootstrapService.class));
        ReflectionTestUtils.setField(siteService, "siteRootDir", tempDir.toString());

        server = new SitePreviewServer(siteService);
        // 端口基数刻意放在一段不会被占用的高位段：起不来时会退回临时端口，用例不依赖具体端口
        ReflectionTestUtils.setField(server, "basePort", UNUSED_BASE_PORT);
        base = server.url(SITE_ID);
    }

    @Test
    @DisplayName("重复取地址拿到同一个端口；根路径给首页")
    void servesHomeFromRoot() throws Exception {
        assertThat(server.url(SITE_ID)).as("重复点按钮不应该换端口").isEqualTo(base);

        HttpResponse<String> response = get("/");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("首页");
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/html; charset=utf-8");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
    }

    @Test
    @DisplayName("目录页：带斜杠直接给 index.html，不带斜杠 301 补斜杠")
    void servesDirectoryPage() throws Exception {
        HttpResponse<String> withSlash = get("/news/tech/");
        assertThat(withSlash.statusCode()).isEqualTo(200);
        assertThat(withSlash.body()).contains("技术与发布");

        HttpResponse<String> withoutSlash = get("/news/tech");
        assertThat(withoutSlash.statusCode()).isEqualTo(301);
        assertThat(withoutSlash.headers().firstValue("Location").orElse(""))
                .as("补斜杠是为了让页内相对地址解析在正确的层级上").endsWith("/news/tech/");

        HttpResponse<String> followed = send("/news/tech", "GET", HttpClient.Redirect.NORMAL);
        assertThat(followed.statusCode()).isEqualTo(200);
        assertThat(followed.body()).contains("技术与发布");
    }

    @Test
    @DisplayName("产物里的单文件页（404.html / feed.xml / css）按各自类型取到")
    void servesSingleFiles() throws Exception {
        assertThat(get("/404.html").body()).contains("没有这个页面");
        assertThat(get("/feed.xml").headers().firstValue("Content-Type").orElse(""))
                .contains("application/xml");
        assertThat(get("/assets/site.css").headers().firstValue("Content-Type").orElse(""))
                .contains("text/css");
        assertThat(get("/assets/site.css").body()).isEqualTo("body{}");
    }

    @Test
    @DisplayName("产物里没有的页面给 404，不给后台的 index.html")
    void missingPageIsNotFound() throws Exception {
        HttpResponse<String> response = get("/news/not-there/");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("预览产物里没有这个页面：news/not-there");
    }

    @Test
    @DisplayName("越界路径一律拒绝：.. 上跳、URL 编码绕过、反斜杠")
    void rejectsEscapingPaths() throws Exception {
        // HttpClient 会在发出去之前把 /../ 规范化掉，所以只能走原始请求行
        assertThat(rawStatus("/")).as("原始请求行取首页").isEqualTo(200);
        assertThat(rawStatus("/pom.xml")).isEqualTo(404);          // 站点里本来就没有这个文件
        assertThat(rawStatus("/../pom.xml")).isEqualTo(400);       // 但上跳到站点目录之外必须被拒绝
        assertThat(rawStatus("/..%5Cpom.xml")).isEqualTo(400);
        assertThat(get("/%2e%2e/pom.xml").statusCode()).isEqualTo(400);
        assertThat(get("/news/%2e%2e/%2e%2e/pom.xml").statusCode()).isEqualTo(400);
    }

    /** 把请求行原样写到 socket 上，绕开客户端的路径规范化 */
    private int rawStatus(String rawPath) throws IOException {
        int port = Integer.parseInt(base.substring(base.lastIndexOf(':') + 1, base.length() - 1));
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(
                    ("GET " + rawPath + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String statusLine = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            assertThat(statusLine).as("预览服务没有回状态行").isNotNull();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    @Test
    @DisplayName("只支持 GET / HEAD，写方法一律 405")
    void rejectsWrites() throws Exception {
        assertThat(send("/index.html", "DELETE", HttpClient.Redirect.NEVER).statusCode()).isEqualTo(405);
    }

    @Test
    @DisplayName("还没发布过（www/ 空着）时给一句能照做的错误")
    void failsWhenNotPublishedYet() throws IOException {
        CmsSite blank = new CmsSite();
        blank.setId(9L);
        blank.setRootDir("blank");
        // 站点建出来时 www/ 就已经存在（SiteService 建的三件套），没有 index.html 才算没发布过
        Files.createDirectories(tempDir.resolve("blank").resolve("www"));
        CmsSiteMapper mapper = mock(CmsSiteMapper.class);
        when(mapper.selectById(anyLong())).thenReturn(blank);
        SiteService siteService = new SiteService(mapper,
                mock(CmsContentMapper.class), mock(CmsCategoryMapper.class), mock(CmsTagMapper.class),
                mock(CmsMediaMapper.class), mock(SysUserSiteMapper.class), mock(SiteBootstrapService.class));
        ReflectionTestUtils.setField(siteService, "siteRootDir", tempDir.toString());
        SitePreviewServer blankServer = new SitePreviewServer(siteService);

        assertThatThrownBy(() -> blankServer.url(9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("站点还没有发布过");
    }
}
