package com.lingjiuw.cms.module.cms.publish.model;

/**
 * 定义版本号（static-publish.md §4.1、§4.4）：类型定义版本 + 字段定义版本 + 站点配置版本，
 * **三者任一保存即 +1**。
 *
 * <p>编译缓存的 key 里必须有它：只按 {@code mtime + size} 失效时，改了字段定义而模板没动，
 * 缓存仍命中用旧定义编译出的 AST，§4.5 的第 4/5/6 条校验全部失效，而且失效方式是
 * "渲染期才炸"（§4.4 的 v2.1 修改）。
 *
 * <p>站点级**菜单不进这里**：菜单是数据，不是定义（§4.4）。
 */
public record DefVersion(long types, long fields, long siteConfig) {

    public static final DefVersion ZERO = new DefVersion(0, 0, 0);

    /** 拼进缓存 key 的形态：{@code 类型版本.字段版本.站点配置版本}。 */
    public String key() {
        return types + "." + fields + "." + siteConfig;
    }

    public DefVersion bumpTypes() {
        return new DefVersion(types + 1, fields, siteConfig);
    }

    public DefVersion bumpFields() {
        return new DefVersion(types, fields + 1, siteConfig);
    }

    public DefVersion bumpSiteConfig() {
        return new DefVersion(types, fields, siteConfig + 1);
    }
}
