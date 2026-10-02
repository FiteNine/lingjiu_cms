package com.lingjiuw.cms.module.cms.publish.preview;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SitePathBoundary;
import com.lingjiuw.cms.module.cms.service.SiteService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 站点预览：把 <b>已发布的产物树</b>（{@code sites/<站点>/www/}）用第二个端口原样提供出来，
 * 后台「发布中心」的「预览站点」按钮直接开这个地址。
 *
 * <h2>为什么必须另占一个端口</h2>
 * 发布产物的 URL 全是**根绝对路径**（`/assets/site.css`、`/news/`、`/uploads/…`，以及
 * {@code site.js} 里的 {@code fetch('/search/index.json')}），这是引擎刻意保持的不变量
 * （交付报告："相对 URL 0 处"）。后台管理界面自己占着 `/`、`/assets/**`，因此
 * **把站点挂在后台路径下是不可能的**：要么整棵树做 URL 重写，要么让站点独占一个 origin。
 * 这里选后者——不重写一个字节，预览看到的就是 nginx 上线后那份产物。
 *
 * <h2>一个站点一个端口</h2>
 * 端口 = {@code cms.preview.port} + 站点 id（默认 8090 + id：演示站 8096）。可见性由端口切开，
 * 因此 <b>没有"当前预览站点"这种全局可变状态</b>——同一时刻两个站点各自一个端口，互不干扰。
 * 端口被占用时退回系统分配的临时端口（预览地址本来就是"谁问谁拿到"的一次性链接）。
 *
 * <h2>安全边界（如实说明）</h2>
 * <ul>
 *   <li>只监听 {@code 127.0.0.1}，即**只有本机浏览器能访问**；这不是公网发布通道，
 *       站点对外的正式出口仍是 nginx {@code alias} 到 {@code sites/<站点>/www}（§8.10 方案 A）；</li>
 *   <li>只读：只回吐 {@code www/} 下的文件，没有任何写方法；路径解析复用
 *       {@link SitePathBoundary#resolveUnder}，{@code ..} 与绝对路径一律拒绝；</li>
 *   <li>只服务**已经落盘的产物**。未发布的草稿（§8.7 的预发布 {@code preview/www/}）
 *       不在这里，那是引擎级改动，另作一期；没有 {@code www/index.html} 就按"还没发布过"拒绝；</li>
 *   <li>{@code /uploads/**} 是 CMS 的媒体出口（{@code cms.upload.dir}），不在本站点的
 *       {@code www/} 树里，所以预览页上的图片会 404——产物里存的是 URL，不是图片本身。</li>
 * </ul>
 */
@Slf4j
@Service
public class SitePreviewServer {

    /** 预览端口基数：真实端口 = 基数 + 站点 id */
    @Value("${cms.preview.port:8090}")
    private int basePort;

    private final SiteService siteService;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "site-preview");
        // 守护线程：预览服务不阻止 JVM 退出
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, Running> running = new ConcurrentHashMap<>();

    public SitePreviewServer(SiteService siteService) {
        this.siteService = siteService;
    }

    /** 一个已经起来的预览服务 */
    private record Running(Path root, HttpServer server) {
    }

    /**
     * 该站点的预览地址；服务没起来就现在起来（幂等，重复点按钮拿到同一个地址）。
     *
     * @throws BizException 站点目录里没有 {@code www/index.html}（从来没发布过）
     */
    public String url(long siteId) {
        Path root = siteService.siteDir(siteId).resolve("www").normalize();
        // 站点建出来时 www/ 就存在（SiteService 的三件套），所以"有没有发布过"的标志只能是首页
        if (!Files.isRegularFile(root.resolve("index.html"), LinkOption.NOFOLLOW_LINKS)) {
            throw new BizException("站点还没有发布过，请先「一键全站静态化」再预览：" + root);
        }
        Running current = running.get(siteId);
        if (current != null && root.equals(current.root())) {
            return address(current.server());
        }
        // 站点目录被改过（站点管理里换了目录）时也要重新起来，root 是判据
        synchronized (running) {
            Running latest = running.get(siteId);
            if (latest != null && root.equals(latest.root())) {
                return address(latest.server());
            }
            if (latest != null) {
                latest.server().stop(0);
                running.remove(siteId);
            }
            Running started = start(siteId, root);
            running.put(siteId, started);
            log.info("站点 {} 的预览服务已启动：{}（根目录 {}）", siteId, address(started.server()), root);
            return address(started.server());
        }
    }

    /** 起一个只服务 root 的 HttpServer：先试 {@code 基数+站点 id}，端口被占用时退回临时端口 */
    private Running start(long siteId, Path root) {
        // 端口必须是 1..65535 的 int：basePort + siteId 用 long 算，越界或截断会让
        // InetSocketAddress 抛 IllegalArgumentException（bind 只捕获 IOException，异常会冒泡打断预览）。
        long port = (long) basePort + siteId;
        HttpServer server = port > 0 && port <= 65535 ? bind((int) port) : null;
        if (server == null) {
            server = bind(0);
        }
        if (server == null) {
            throw new BizException("预览服务启动失败：本机没有可用的端口");
        }
        server.createContext("/", exchange -> handle(exchange, root));
        server.setExecutor(executor);
        server.start();
        return new Running(root, server);
    }

    /** 端口被占用或端口非法返回 null，交给调用方换端口；不在这里报错，占用不是异常情况 */
    private static HttpServer bind(int port) {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String address(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    /** 静态文件出口：磁盘每次读，浏览器每次取最新的产物（不缓存） */
    private static void handle(HttpExchange exchange, Path root) {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain; charset=utf-8", "只支持 GET / HEAD".getBytes());
                return;
            }
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            // Windows 上 \ 也是分隔符：resolveUnder 会把 \ 换成 /，等于放行 %5C 绕过，这里先拦一道
            if (path.indexOf('\\') >= 0) {
                send(exchange, 400, "text/plain; charset=utf-8", "路径不合法".getBytes());
                return;
            }
            // 请求路径一律按"站点根目录下的相对路径"喂给 SitePathBoundary，由它负责越界判断
            String relative = path.startsWith("/") ? path.substring(1) : path;
            if (relative.endsWith("/")) {
                relative = relative.substring(0, relative.length() - 1);
            }
            Path target;
            try {
                target = SitePathBoundary.resolveUnder(root, relative);
            } catch (BizException e) {
                send(exchange, 400, "text/plain; charset=utf-8", "路径不合法".getBytes());
                return;
            }
            // 产物两种落点：/about/index.html（目录页）与 /404.html（单文件），都认
            boolean singleFile = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
            if (!singleFile && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) && !path.endsWith("/")) {
                // 目录页补斜杠：少了它，页内相对地址会按上一级解析（nginx 也是这么做的）
                exchange.getResponseHeaders().set("Location", uri.getRawPath() + "/");
                send(exchange, 301, "text/plain; charset=utf-8", "目录页请带结尾斜杠".getBytes());
                return;
            }
            Path file = relative.isEmpty() ? root.resolve("index.html")
                    : SitePathBoundary.resolveUnder(root, relative + "/index.html");
            if (!singleFile && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                send(exchange, 404, "text/plain; charset=utf-8",
                        ("预览产物里没有这个页面：" + (relative.isEmpty() ? "/" : relative)).getBytes());
                return;
            }
            sendFile(exchange, singleFile ? target : file);
        } catch (IOException e) {
            // 带上异常对象（原来只记 e.getMessage()，栈与原因都丢了）；注意这一层也可能在
            // sendResponseHeaders 之后才抛（Files.size 与 Files.copy 之间文件被重发布替换），
            // 那种情况下客户端只会收到半截响应——日志里有异常本身才能分辨。
            log.warn("预览服务读取出错：{}", exchange.getRequestURI(), e);
        }
    }

    private static void sendFile(HttpExchange exchange, Path file) throws IOException {
        long size = Files.size(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file));
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, "HEAD".equals(exchange.getRequestMethod()) ? -1 : size);
        if ("HEAD".equals(exchange.getRequestMethod())) {
            return;
        }
        try (OutputStream out = exchange.getResponseBody()) {
            Files.copy(file, out);
        }
    }

    /** 产物里的文件类型就这么几种（§7.2.2 的聚合产物 + 图片 + 字体），按扩展名给一遍 */
    private static String contentType(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "html", "htm" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js", "mjs" -> "text/javascript; charset=utf-8";
            case "json" -> "application/json; charset=utf-8";
            case "xml" -> "application/xml; charset=utf-8";
            case "txt", "md" -> "text/plain; charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "avif" -> "image/avif";
            case "bmp" -> "image/bmp";
            case "ico" -> "image/x-icon";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "ttf" -> "font/ttf";
            case "otf" -> "font/otf";
            case "eot" -> "application/vnd.ms-fontobject";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // HEAD 不带响应体（与 sendFile 同口径）：声明长度后不写 body 会让客户端等一个永远不来的正文
        boolean head = "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(status, head ? -1 : body.length);
        if (head) {
            return;
        }
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @PreDestroy
    void shutdown() {
        running.values().forEach(item -> item.server().stop(0));
        running.clear();
        executor.shutdownNow();
    }
}
