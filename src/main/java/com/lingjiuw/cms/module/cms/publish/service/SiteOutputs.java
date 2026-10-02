package com.lingjiuw.cms.module.cms.publish.service;

import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.OrderBy;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 非页面产物（static-publish.md §7.2.2 的"生成者"列、§7.5、§7.6）。
 *
 * <p>七种产物：{@code sitemap.xml} + 分片、{@code feed.xml}、{@code robots.txt}、
 * {@code redirects.conf}、{@code cms-site.json}、{@code search/index.json} + 分片。
 *
 * <p><b>它们每次全量重写</b>（不是增量追加），因此"幽灵条目"这件事在结构上就不存在——
 * 只会少，不会多（§8.6 最后一条）。这也是把它们与页面渲染分开的原因：页面的正确性靠
 * {@code manifest} + GC，聚合产物的正确性靠"每次重算"。
 *
 * <p>两条一致性要求落在这一处实现（不允许模板或别处再算一遍）：
 * <ul>
 *   <li><b>sitemap 只收录"进了计划的 S 类页面且 {@code noindex=0}"</b>（§7.6）；</li>
 *   <li><b>sitemap 与 noindex 由同一个判定函数产出</b>——{@code noindex} 的值由
 *       {@link PageRenderer} 算好放进 {@code page} 作用域，这里直接读同一个值（§7.6 原话：
 *       "两处不一致是 SEO 事故的常见来源"）。</li>
 * </ul>
 */
@Slf4j
public final class SiteOutputs {

    /** 引擎版本号：写进 {@code cms-site.json}，供 {@code cms.js} 与排障用（§11.1）。 */
    public static final String ENGINE_VERSION = "2.2";

    private static final DateTimeFormatter W3C_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /**
     * RSS 的 {@code pubDate} 形态。
     *
     * <p><b>不能带 {@code Z} / {@code XXX}</b>：契约把时间列定成 {@code timestamp}（不带时区，
     * 见 §5.2 的已知偏离），映射到 Java 是 {@link LocalDateTime}，而带 offset 的模式去 format 一个
     * {@code LocalDateTime} 会抛 {@code Unsupported field: OffsetSeconds}。这条错误曾经把整个
     * 第 ⑥ 阶段（聚合产物）拖下水——feed 一炸，同一次 {@code try} 里的 robots / 索引 /
     * {@code cms-site.json} 全都不产出。此处按站点时区（部署写死 GMT+8）拼后缀。
     */
    private static final DateTimeFormatter RFC822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss", java.util.Locale.US);

    /** 站点固定时区后缀：{@code application.yml} 把时区写死为 GMT+8（§5.2 的偏离记录）。 */
    private static final String RSS_ZONE_SUFFIX = " +0800";

    private final ContentProvider provider;
    private final ArtifactWriter writer;
    private final SiteConfig site;

    /**
     * 本批次的"内容水位"：全部页面依赖内容里最大的 {@code updateTime}。
     *
     * <p>它替代"生成时刻"出现在 sitemap 的 {@code <lastmod>}、feed 的 {@code <lastBuildDate>}
     * 与搜索索引的 {@code version} 上。理由只有一条：**发布必须幂等**（§8.4："同一输入产出同一结果"）。
     * 写 {@code now()} 会让"同一份内容发布两次"产生两份不同字节的产物——增量发布的 diff、
     * 产物哈希、黄金文件比对全部失效。内容水位是真正的输入摘要：内容没变，它就一模一样；
     * 内容变了，它必然变大。
     */
    private LocalDateTime contentWatermark;

    public SiteOutputs(ContentProvider provider, ArtifactWriter writer) {
        this.provider = provider;
        this.writer = writer;
        this.site = provider.site();
    }

    /** 一个已渲染页面的元信息（sitemap 与索引都从它取数，避免两处各算一遍）。 */
    public record RenderedPage(Plan plan, Map<String, Object> pageScope, String html,
                               long lastModified) {

        public boolean noindex() {
            Object value = pageScope.get("noindex");
            return value instanceof Boolean flag ? flag : "1".equals(String.valueOf(value));
        }
    }

    /**
     * sitemap 索引与分片（§7.6）。
     *
     * @param pages 本次渲染出的全部页面。**顺序必须是确定的**：调用方要先按 URL 排好序再传进来，
     *              否则并发渲染的完成顺序会让同一份输入产出不同字节的 sitemap（§8.4 要求幂等）。
     * @return 产出的文件清单（相对 {@code www/}）
     */
    public List<String> writeSitemap(List<RenderedPage> pages) {
        observeWatermark(pages);
        int shardSize = Math.max(1, site.number("sitemap.shardSize", 10000));
        List<RenderedPage> indexable = new ArrayList<>();
        for (RenderedPage page : pages) {
            // 只收录 S 类页面且 noindex=0 的页面；DPAGE / SEARCH / 404 / feed 不进 sitemap。
            // FACET 在 isIndexable 里是"可收录"，但是否真的收录由 noindex 兜底：
            // 筛选页默认 noindex（§7.4 第（5）条），只有站点选项 seo.facetIndex 打开时才进 sitemap。
            if (page.noindex()) {
                continue;
            }
            if (!isIndexable(page.plan().pageType())) {
                continue;
            }
            indexable.add(page);
        }
        List<String> written = new ArrayList<>();
        List<String> shards = new ArrayList<>();
        for (int offset = 0; offset < indexable.size(); offset += shardSize) {
            int index = offset / shardSize;
            int end = Math.min(offset + shardSize, indexable.size());
            List<RenderedPage> shard = indexable.subList(offset, end);
            String name = "sitemap-" + index + ".xml";
            writer.write(name, urlset(shard));
            written.add(name);
            shards.add(name);
        }
        if (shards.isEmpty()) {
            // 一个页面都不收录时也要出一个合法的空 urlset（否则 sitemap.xml 指向的分片不存在）
            writer.write("sitemap-0.xml", urlset(List.of()));
            written.add("sitemap-0.xml");
            shards.add("sitemap-0.xml");
        }
        writer.write("sitemap.xml", sitemapIndex(shards));
        written.add("sitemap.xml");
        return written;
    }

    /** 哪些页面类型进 sitemap（只有 S 类，且排除 DPAGE 的第 2..N 页、SEARCH、404、feed）。 */
    private static boolean isIndexable(com.lingjiuw.cms.module.cms.publish.model.PageType pageType) {
        return switch (pageType) {
            case HOME, LIST, TAGLIST, TAGPAGE, ARCHIVE, DETAIL, SINGLE, FACET, STATIC -> true;
            case DPAGE, SEARCH, PAGE404, FEED -> false;
        };
    }

    private String urlset(List<RenderedPage> pages) {
        StringBuilder xml = new StringBuilder(256 + pages.size() * 160);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        // 百度移动 sitemap 协议：站点声明了"同时适配 PC 与移动端"（站点选项 seo.applicableDevice，
        // 页面里的 <meta name="applicable-device"> 就是它）时，按百度《移动网站如何快速向百度提交数据》
        // 的做法在 sitemap 里逐条声明一次。谷歌会忽略这个专有命名空间，不影响它在谷歌侧解析。
        String device = deviceDeclaration();
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"");
        if (device != null) {
            xml.append("\n        xmlns:mobile=\"http://www.baidu.com/schemas/sitemap-mobile/1/\"");
        }
        xml.append(">\n");
        for (RenderedPage page : pages) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(escape(absolute(page.plan().url()))).append("</loc>\n");
            if (device != null) {
                xml.append("    <mobile:mobile type=\"").append(escape(device)).append("\"/>\n");
            }
            LocalDateTime lastmod = lastModifiedOf(page);
            if (lastmod != null) {
                xml.append("    <lastmod>").append(lastmod.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .append("</lastmod>\n");
            }
            xml.append("  </url>\n");
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }

    /** 站点声明的适用设备（{@code pc,mobile} 这类）；没声明返回 null = 不写 mobile 命名空间。 */
    private String deviceDeclaration() {
        String value = provider.site().option("seo.applicableDevice", "");
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String sitemapIndex(List<String> shards) {
        StringBuilder xml = new StringBuilder(256 + shards.size() * 120);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        String lastmod = watermarkText();
        for (String shard : shards) {
            xml.append("  <sitemap>\n");
            xml.append("    <loc>").append(escape(absolute("/" + shard))).append("</loc>\n");
            xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
            xml.append("  </sitemap>\n");
        }
        xml.append("</sitemapindex>\n");
        return xml.toString();
    }

    /**
     * 记录"本批次的内容水位"：全部页面依赖内容里最大的 {@code updateTime}。
     *
     * <p>它必须由**输入**算出来，不能是 {@code now()}——否则同一份内容发布两次会产生两份不同字节的
     * 聚合产物，"发布幂等"（§8.5）就没法验证，增量发布的产物哈希比对也会永远判"变了"。
     * 所有页面都没有可用的 {@code updateTime} 时退回到一个固定常量（而不是当前时刻）。
     */
    public void observeWatermark(List<RenderedPage> pages) {
        if (contentWatermark != null) {
            return;
        }
        LocalDateTime newest = null;
        for (RenderedPage page : pages) {
            LocalDateTime candidate = lastModifiedOf(page);
            if (candidate != null && (newest == null || candidate.isAfter(newest))) {
                newest = candidate;
            }
        }
        contentWatermark = newest == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : newest;
    }

    /**
     * 从**数据层**算内容水位。
     *
     * <p>{@link #observeWatermark(List)} 从"本次渲染出的页面"算，只覆盖进了计划的页面；
     * 这一个直接问数据层，因此**不依赖调用顺序**（feed / robots / 站点标识可能在页面写完之前写出）。
     * 空站点（一条内容都没有）得到那个固定常量。
     */
    public LocalDateTime watermarkFromData() {
        LocalDateTime newest = null;
        for (com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef type : provider.types()) {
            if (type.kind() == com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef.Kind.SINGLE) {
                continue;
            }
            for (ContentItem item : provider.query(new ContentQuery().type(type.code())
                    .category("all").row(0)).rows()) {
                LocalDateTime candidate = timeOf(item, "updateTime", "publishTime");
                if (candidate != null && (newest == null || candidate.isAfter(newest))) {
                    newest = candidate;
                }
            }
        }
        contentWatermark = newest == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : newest;
        return contentWatermark;
    }

    /** 内容水位；还没观察过时用固定常量（保证确定性）。 */
    private String watermarkText() {
        return watermark().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private LocalDateTime watermark() {
        return contentWatermark == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : contentWatermark;
    }

    /**
     * 一条 sitemap 记录的 {@code lastmod} = 该页面**输入集合**里内容的最大 {@code updateTime}
     * （§7.6：这正是 §8.4 依赖清单的副产品）。
     */
    private LocalDateTime lastModifiedOf(RenderedPage page) {
        LocalDateTime newest = null;
        if (page.plan().entry() != null) {
            newest = timeOf(page.plan().entry(), "updateTime", "publishTime");
        }
        for (Long id : page.plan().contentIds()) {
            ContentItem item = provider.contentById(id);
            if (item == null) {
                continue;
            }
            LocalDateTime candidate = timeOf(item, "updateTime", "publishTime");
            if (candidate != null && (newest == null || candidate.isAfter(newest))) {
                newest = candidate;
            }
        }
        return newest;
    }

    /**
     * feed（§7.6）：最近 {@code feed.size} 条 {@code feed.types} 类型的内容。
     *
     * @return 产出的文件清单
     */
    public List<String> writeFeed() {
        if (!site.flag("page.feed", true)) {
            return List.of();
        }
        // 与 writeSearchIndex 同口径：水位没建立时先由数据层算出来，别写出 1970-01-01 的固定水位
        if (contentWatermark == null) {
            watermarkFromData();
        }
        List<String> types = stringList(site.options().get("feed.types"));
        if (types.isEmpty()) {
            types = List.of("article");
        }
        int size = Math.max(1, site.number("feed.size", 20));
        List<ContentItem> items = new ArrayList<>();
        for (String type : types) {
            if (provider.type(type) == null) {
                continue;
            }
            items.addAll(provider.query(new ContentQuery().type(type).category("all").row(size)
                    .orderby(List.of(new OrderBy("publishTime", true)))).rows());
        }
        // 整体倒序但**缺时间的条目不跟着反转**：直接 .reversed() 会把 nullsLast 反转成
        // "null 最小"，于是时间取不到的条目反而排到 feed 最前面。
        items.sort(java.util.Comparator.comparing(
                (ContentItem item) -> timeOf(item, "publishTime", "updateTime"),
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        if (items.size() > size) {
            items = items.subList(0, size);
        }
        writer.write("feed.xml", feed(items));
        return List.of("feed.xml");
    }

    private String feed(List<ContentItem> items) {
        boolean atom = "atom".equalsIgnoreCase(site.option("feed.format", "rss"));
        boolean includeBody = site.flag("feed.includeBody", false);
        StringBuilder xml = new StringBuilder(1024 + items.size() * 640);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        if (atom) {
            xml.append("<feed xmlns=\"http://www.w3.org/2005/Atom\">\n");
            xml.append("  <title>").append(escape(site.name())).append("</title>\n");
            xml.append("  <link href=\"").append(escape(absolute("/feed.xml"))).append("\"/>\n");
            xml.append("  <link href=\"").append(escape(absolute("/"))).append("\"/>\n");
            xml.append("  <updated>").append(watermarkText()).append("</updated>\n");
            xml.append("  <id>").append(escape(absolute("/"))).append("</id>\n");
            for (ContentItem item : items) {
                String url = absolute(String.valueOf(item.get("url") == null ? "/" : item.get("url")));
                xml.append("  <entry>\n");
                xml.append("    <title>").append(escape(item.title())).append("</title>\n");
                xml.append("    <link href=\"").append(escape(url)).append("\"/>\n");
                xml.append("    <id>").append(escape(url)).append("</id>\n");
                xml.append("    <updated>").append(isoOf(timeOf(item, "updateTime", "publishTime")))
                        .append("</updated>\n");
                appendAtomSummary(xml, item, includeBody);
                xml.append("  </entry>\n");
            }
            xml.append("</feed>\n");
            return xml.toString();
        }
        xml.append("<rss version=\"2.0\">\n<channel>\n");
        xml.append("  <title>").append(escape(site.name())).append("</title>\n");
        xml.append("  <link>").append(escape(absolute("/"))).append("</link>\n");
        xml.append("  <description>").append(escape(site.description() == null ? site.name()
                : site.description())).append("</description>\n");
        xml.append("  <language>").append(escape(site.lang())).append("</language>\n");
        xml.append("  <lastBuildDate>").append(rfc822Of(watermark())).append(RSS_ZONE_SUFFIX)
                .append("</lastBuildDate>\n");
        for (ContentItem item : items) {
            String url = absolute(String.valueOf(item.get("url") == null ? "/" : item.get("url")));
            xml.append("  <item>\n");
            xml.append("    <title>").append(escape(item.title())).append("</title>\n");
            xml.append("    <link>").append(escape(url)).append("</link>\n");
            xml.append("    <guid isPermaLink=\"true\">").append(escape(url)).append("</guid>\n");
            appendRssBody(xml, item, includeBody);
            xml.append("    <pubDate>").append(rfc822Of(timeOf(item, "publishTime", "updateTime")))
                    .append("</pubDate>\n");
            // <enclosure> 用条目的 FILE 字段（url / filesize / mime，§7.6 v2.2）
            appendEnclosure(xml, item);
            xml.append("  </item>\n");
        }
        xml.append("</channel>\n</rss>\n");
        return xml.toString();
    }

    private void appendRssBody(StringBuilder xml, ContentItem item, boolean includeBody) {
        String body = includeBody ? bodyOf(item) : summaryOf(item);
        if (body != null && !body.isEmpty()) {
            xml.append("    <description><![CDATA[").append(cdata(body)).append("]]></description>\n");
        }
    }

    private void appendAtomSummary(StringBuilder xml, ContentItem item, boolean includeBody) {
        String body = includeBody ? bodyOf(item) : summaryOf(item);
        if (body != null && !body.isEmpty()) {
            xml.append("    <summary type=\"html\"><![CDATA[").append(cdata(body))
                    .append("]]></summary>\n");
        }
    }

    private void appendEnclosure(StringBuilder xml, ContentItem item) {
        Object files = item.get("files");
        Map<String, Object> file = firstMap(files);
        if (file == null) {
            return;
        }
        String url = text(file.get("url"));
        if (url == null) {
            return;
        }
        String mime = text(file.get("mime"));
        Object size = file.get("size");
        xml.append("    <enclosure url=\"").append(escape(absolute(url))).append("\" length=\"")
                .append(size instanceof Number number ? number.longValue() : 0L).append("\" type=\"")
                .append(escape(mime == null ? "application/octet-stream" : mime)).append("\"/>\n");
    }

    private String bodyOf(ContentItem item) {
        Object html = item.get("contentHtml");
        if (html == null || String.valueOf(html).isBlank()) {
            html = item.get("content");
        }
        return html == null ? null : String.valueOf(html);
    }

    private String summaryOf(ContentItem item) {
        Object summary = item.get("summary");
        if (summary != null && !String.valueOf(summary).isBlank()) {
            return String.valueOf(summary);
        }
        String body = bodyOf(item);
        if (body == null) {
            return null;
        }
        String plain = body.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        return plain.length() > 200 ? plain.substring(0, 200) + "…" : plain;
    }

    /**
     * {@code robots.txt}（§7.6）：主题提供模板就用它，否则用内置默认值。
     *
     * <p>主题模板里允许写 {@code [field:site.xxx/]}——它不是页面模板（没有渲染上下文、也没有
     * {@code {cms:}} 标签），因此这里对 {@code site} 作用域做一次**字面量替换**。
     * 不做这一步的后果是实打实的：主题里写 {@code Sitemap: [field:site.url/]/sitemap.xml} 时，
     * 产物里会原样留着那个占位符，搜索引擎读到一条无效的 Sitemap 声明——而 robots 正是发布后
     * 没人会去逐字读、却对收录影响最大的那个文件。
     *
     * @param themeTemplate 主题里 {@code robots.txt} 的内容；null = 用内置默认
     */
    public void writeRobots(String themeTemplate) {
        String content = themeTemplate;
        if (content == null || content.isBlank()) {
            content = "User-agent: *\n"
                    + "Disallow: /api/\n"
                    + "Disallow: /search/\n"
                    + "Allow: /\n"
                    + "\n"
                    + "Sitemap: " + absolute("/sitemap.xml") + "\n";
        } else {
            content = substituteSiteFields(content);
        }
        writer.write("robots.txt", content);
    }

    /** 把 {@code [field:site.<key>/]} 换成站点作用域里的取值；未知 key 原样保留（不静默清空）。 */
    private String substituteSiteFields(String text) {
        Map<String, Object> scope = site.toScope();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\[field:site\\.([A-Za-z][A-Za-z0-9]*)/]")
                .matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 64);
        while (matcher.find()) {
            Object value = scope.get(matcher.group(1));
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
                    value == null ? matcher.group(0) : String.valueOf(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * {@code redirects.conf}（§7.6）：由 {@code cms_redirect} 表生成。
     *
     * <p>{@code cms_redirect} 表在本期还不存在（§12.1 的清单里没有它），因此这里出一个
     * **空的合法文件**并留痕——比"文件不生成"好：nginx 的 {@code include} 指令不会因为缺文件
     * 而在启动时报错。表随期 4 一起建，届时只需在这一处把查询接上。
     */
    public void writeRedirects() {
        if (!site.flag("page.redirect", true)) {
            return;
        }
        writer.write("redirects.conf", "# 由引擎生成：cms_redirect 表尚无记录（表随期 4 建立）\n");
    }

    /** {@code cms-site.json}（§7.6、§11.1）：{@code cms.js} 靠它认站点，运维靠它判"本批次已完整"。 */
    public void writeSiteMarker() {
        // 单独复用本方法时水位可能还没建立：先算出来，别写出 1970 常量（与 writeSearchIndex 同口径）
        if (contentWatermark == null) {
            watermarkFromData();
        }
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("siteId", site.id());
        marker.put("siteCode", site.code());
        marker.put("lang", site.lang());
        marker.put("engineVersion", ENGINE_VERSION);
        // 写"内容水位"而不是 now()：这个文件是"本批次产物已完整"的标记（§8.5），
        // 而标记的内容必须是输入的摘要——否则同一份内容发两次会得到两份不同的标记文件。
        marker.put("publishedAt", watermarkText());
        writer.write("cms-site.json", json(marker));
    }

    /**
     * 搜索索引（§7.5）：{@code search/index.json} + {@code search/shard-{k}.json}。
     *
     * <p>搜索关闭（{@code search.mode='off'} 或 {@code page.search=0}）时不出索引。
     *
     * @return 产出的文件清单
     */
    public List<String> writeSearchIndex() {
        if (!site.flag("page.search", true)) {
            return List.of();
        }
        String mode = site.option("search.mode", "static");
        if ("off".equalsIgnoreCase(mode)) {
            return List.of();
        }
        // version 由内容水位算出来（不依赖调用顺序）
        if (contentWatermark == null) {
            watermarkFromData();
        }
        int bodyChars = Math.max(0, site.number("search.bodyChars", 1000));
        int shardSize = Math.max(1, site.number("index.shardSize", 2000));
        List<Map<String, Object>> entries = new ArrayList<>();
        for (com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef type : provider.types()) {
            if (type.kind() == com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef.Kind.SINGLE) {
                continue;
            }
            if (Boolean.FALSE.equals(type.options().get("searchable"))) {
                continue;
            }
            for (ContentItem item : provider.query(new ContentQuery().type(type.code())
                    .category("all").row(0)).rows()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("i", item.id());
                entry.put("t", item.title());
                entry.put("u", item.get("url"));
                entry.put("s", item.get("summary"));
                entry.put("c", categoryText(item));
                entry.put("p", dateText(timeOf(item, "publishTime", "updateTime")));
                // 正文只留 searchable=1 的字段拼成的纯文本，并截断到 search.bodyChars（体积最贵）
                entry.put("b", truncate(plainText(item), bodyChars));
                entries.add(entry);
            }
        }
        List<String> shards = new ArrayList<>();
        for (int offset = 0; offset < entries.size(); offset += shardSize) {
            int index = offset / shardSize;
            int end = Math.min(offset + shardSize, entries.size());
            String name = "search/shard-" + index + ".json";
            writer.write(name, jsonArray(entries.subList(offset, end)));
            shards.add("shard-" + index + ".json");
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        // version 是"索引版本"，由内容水位算出来而不是取时刻：同一份内容两次发布的索引字节相同，
        // 内容变了版本自然变——这正是前端缓存失效需要的语义（§7.5）。
        manifest.put("version", watermarkText());
        manifest.put("shards", shards);
        manifest.put("count", entries.size());
        manifest.put("fields", List.of("i", "t", "u", "s", "c", "p", "b"));
        writer.write("search/index.json", json(manifest));

        List<String> written = new ArrayList<>();
        written.add("search/index.json");
        for (String shard : shards) {
            written.add("search/" + shard);
        }
        return written;
    }

    private static String categoryText(ContentItem item) {
        Object name = item.get("categoryName");
        List<String> parts = new ArrayList<>();
        if (name != null && !String.valueOf(name).isBlank()) {
            parts.add(String.valueOf(name));
        }
        Object tags = item.get("tags");
        if (tags instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> map && map.get("name") != null) {
                    parts.add(String.valueOf(map.get("name")));
                }
            }
        }
        return String.join(" ", parts);
    }

    /** 只取 searchable=1 的字段拼纯文本（§7.5 第（1）条）。 */
    private String plainText(ContentItem item) {
        StringBuilder text = new StringBuilder();
        com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef type =
                item.typeCode() == null ? null : provider.type(item.typeCode());
        if (type != null) {
            for (com.lingjiuw.cms.module.cms.publish.model.FieldDef field : type.fields()) {
                if (!field.searchable()) {
                    continue;
                }
                Object value = item.get(field.code());
                if (value == null) {
                    continue;
                }
                text.append(String.valueOf(value)).append(' ');
            }
        }
        Object body = item.get("content");
        if (body != null) {
            text.append(body);
        }
        return text.toString().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return max > 0 && text.length() > max ? text.substring(0, max) : text;
    }

    private static String dateText(LocalDateTime time) {
        return time == null ? "" : time.format(W3C_DATE);
    }

    /** {@code data}/ 目录里的人工资产不进产物树；媒体出口靠 nginx（§7.7 第（5）条）。 */

    /* ---------------- 取值与序列化助手 ---------------- */

    private String absolute(String url) {
        String base = site.url();
        if (base == null || base.isBlank()) {
            return url;
        }
        return base + (url == null ? "" : url);
    }

    private static LocalDateTime timeOf(ContentItem item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value instanceof LocalDateTime time) {
                return time;
            }
            if (value instanceof CharSequence text && !text.toString().isBlank()) {
                try {
                    return LocalDateTime.parse(text.toString());
                } catch (RuntimeException ignored) {
                    // 换个 key 再试
                }
            }
        }
        return null;
    }

    private static String isoOf(LocalDateTime time) {
        return (time == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : time)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static String rfc822Of(LocalDateTime time) {
        return (time == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : time).format(RFC822);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    private static Map<String, Object> firstMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return firstMap(list.get(0));
        }
        return null;
    }

    private static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> values = new ArrayList<>();
            for (Object element : list) {
                String text = text(element);
                if (text != null) {
                    values.add(text);
                }
            }
            return values;
        }
        if (raw instanceof CharSequence text && !text.toString().isBlank()) {
            List<String> values = new ArrayList<>();
            for (String element : text.toString().split(",")) {
                String trimmed = element.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            return values;
        }
        return List.of();
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** CDATA 里不能出现 {@code ]]>}，按 XML 规范拆成两段。 */
    private static String cdata(String text) {
        return text == null ? "" : text.replace("]]>", "]]]]><![CDATA[>");
    }

    private static String json(Object value) {
        try {
            return JsonShared.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n";
        } catch (Exception e) {
            log.warn("序列化 JSON 失败：{}", e.getMessage());
            return "{}";
        }
    }

    private static String jsonArray(List<?> values) {
        try {
            return JsonShared.MAPPER.writeValueAsString(values) + "\n";
        } catch (Exception e) {
            log.warn("序列化 JSON 数组失败：{}", e.getMessage());
            return "[]";
        }
    }

    /** 与 {@link JsonSupport} 共用同一个 ObjectMapper（配置只有一处）。 */
    private static final class JsonShared {
        static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
