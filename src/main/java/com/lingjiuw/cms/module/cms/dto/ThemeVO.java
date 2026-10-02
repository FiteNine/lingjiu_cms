package com.lingjiuw.cms.module.cms.dto;

/**
 * 主题视图对象。主题是目录不是表：{@code code} 取 {@code template/} 下的目录名（它才是写进
 * {@code cms_site.theme} 的值），其余展示信息来自该目录的 {@code theme.json}。
 *
 * @param code        目录名，即 {@code cms_site.theme} 的取值
 * @param name        theme.json 里的显示名；缺失或非法 JSON 时兜底为目录名
 * @param version     theme.json 里的版本号
 * @param description theme.json 里的说明
 * @param path        相对站点目录的路径，如 {@code template/lingjiuw}
 * @param valid       theme.json 是否存在且能解析成 JSON 对象；false 表示这个主题元数据不可用
 */
public record ThemeVO(
        String code,
        String name,
        String version,
        String description,
        String path,
        boolean valid) {
}
