package com.lingjiuw.cms.module.cms.publish.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 站点配置与发布选项（static-publish.md §2.7）。{@code site} 作用域的 key 是**封闭清单**，
 * 因此这里给出的访问器就是清单本身：越出清单的 key 一律在编译期报 E1004。
 *
 * <p>三个派生 key 由引擎算出来（§2.7）：{@code url}（站点根绝对地址）、{@code year}（当前年份）、
 * {@code alternates}（多语言配对列表）。它们不进库里，进作用域。
 */
public record SiteConfig(long id, String code, String name, String domain, String protocol,
                         String logo, String lang, String description, String keywords,
                         String seoDescription, String icp, String contactPhone, String contactEmail,
                         String rootDir, String defaultCover, String ogImage, String theme,
                         String statisticsCode, String mediaHost,
                         Map<String, Object> options) {

    public SiteConfig {
        // Map.copyOf 对 null 键/值零容忍，且迭代顺序在 JDK 规范里是 unspecified；
        // options 来自站点选项表（已按 option_code 升序），这里显式过滤 null 并保留插入顺序，
        // 让 optionTree() 的产物顺序可复现（§8.5 要求"同一输入产出同一结果"）。
        if (options == null) {
            options = Map.of();
        } else {
            Map<String, Object> copy = new LinkedHashMap<>();
            options.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
            options = java.util.Collections.unmodifiableMap(copy);
        }
    }

    /** {@code site.url}：站点根绝对地址，= {@code protocol://domain}。 */
    public String url() {
        String scheme = protocol == null || protocol.isBlank() ? "https" : protocol;
        String host = host(domain);
        return host.isEmpty() ? "" : scheme + "://" + host;
    }

    /**
     * 域名规范化：管理员在 {@code cms_site.domain} 里可能连协议、路径或尾斜杠一起填进来，
     * 直接拼接会产出 {@code https://https://example.com/} 这种根地址（canonical、sitemap、
     * 模板里的 {@code site.url} 全都跟着错）。这里只去协议前缀与尾部斜杠，不做别的改写。
     */
    private static String host(String domain) {
        String text = domain == null ? "" : domain.trim();
        int scheme = text.indexOf("://");
        if (scheme >= 0) {
            text = text.substring(scheme + 3);
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /** {@code site.year}：当前年份，页脚版权用。 */
    public int year() {
        return java.time.LocalDate.now().getYear();
    }

    /** 取一个发布选项；不存在时用默认值。 */
    public String option(String code, String defaultValue) {
        Object value = options.get(code);
        return value == null ? defaultValue : String.valueOf(value);
    }

    /** 取一个布尔发布选项。 */
    public boolean flag(String code, boolean defaultValue) {
        Object value = options.get(code);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    /** 取一个整数发布选项。 */
    public int number(String code, int defaultValue) {
        Object value = options.get(code);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 组装 {@code site} 作用域的 22 个 key（§5.1 第（2）条的封闭清单）+ 一个**开放前缀**
     * {@code option}。
     *
     * <p>为什么 {@code option} 是开放前缀而不是第 23 个闭 key：站点发布选项天生是可增可改的
     * 配置（本站加一条 {@code contact.wechat}，模板里就能用），把它们逐个列进清单等于每加一个
     * 选项都要动引擎。模板里写成 {@code [field:site.option.contact.wechat/]}。
     *
     * <p>{@code alternates} 由调用方另行放入（它是列表，且要按当前站点算 {@code current}）。
     */
    public Map<String, Object> toScope() {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("id", id);
        scope.put("code", code);
        scope.put("name", name);
        scope.put("domain", domain);
        scope.put("url", url());
        scope.put("protocol", protocol);
        scope.put("logo", logo);
        scope.put("lang", lang);
        scope.put("description", description);
        scope.put("keywords", keywords);
        scope.put("seoDescription", seoDescription);
        scope.put("icp", icp);
        scope.put("contactPhone", contactPhone);
        scope.put("contactEmail", contactEmail);
        scope.put("rootDir", rootDir);
        scope.put("defaultCover", defaultCover);
        scope.put("ogImage", ogImage);
        scope.put("theme", theme);
        scope.put("statisticsCode", statisticsCode);
        scope.put("mediaHost", mediaHost);
        scope.put("year", year());
        scope.put("alternates", java.util.List.of());
        scope.put("option", optionTree());
        return scope;
    }

    /**
     * {@code site.option.<code>} 的载体：把发布选项按 {@code .} 拆成**嵌套 Map**，
     * 于是 {@code [field:site.option.contact.wechat/]} 能逐段下钻取到值
     * （字段路径是按点分段解析的，扁平 key 里的点会被当成路径分隔符，取不到值）。
     *
     * <p>值的形态保持原样（字符串 / 列表 / Map）——{@link #options} 由数据层解析过 JSON，
     * 这里再转字符串会把 {@code pages.static} 这类结构化选项弄坏。
     */
    private Map<String, Object> optionTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            String[] segments = entry.getKey().split("\\.");
            Map<String, Object> cursor = tree;
            for (int i = 0; i < segments.length - 1; i++) {
                Object child = cursor.get(segments[i]);
                if (child instanceof Map<?, ?> map) {
                    Map<String, Object> existing = new LinkedHashMap<>();
                    map.forEach((key, value) -> existing.put(String.valueOf(key), value));
                    cursor.put(segments[i], existing);
                    cursor = existing;
                } else {
                    Map<String, Object> created = new LinkedHashMap<>();
                    cursor.put(segments[i], created);
                    cursor = created;
                }
            }
            cursor.put(segments[segments.length - 1], entry.getValue());
        }
        return tree;
    }
}
