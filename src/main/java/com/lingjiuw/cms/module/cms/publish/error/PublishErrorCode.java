package com.lingjiuw.cms.module.cms.publish.error;

/**
 * 模板与发布流程的错误码（static-publish.md §10.2）。段位含义：
 * <ul>
 *   <li>{@code E1xxx} 模板语法与编译期；</li>
 *   <li>{@code E2xxx} 定义与查询参数（编译期 / 计划期）；</li>
 *   <li>{@code E3xxx} 页面结构；</li>
 *   <li>{@code E4xxx} URL 与页面计划；</li>
 *   <li>{@code E6xxx} 文件与路径；</li>
 *   <li>{@code E7xxx} H 类接口（面向访客）；</li>
 *   <li>{@code W5xxx} 警告（不阻断发布）。</li>
 * </ul>
 *
 * <p>本枚举按 §10.2 的清单逐行落地。{@code E3011} 在 v2.2 已废弃（与 {@code E2010} /
 * {@code E2011} 重复），因此**不在此枚举里**——历史日志见到它按那两个码处理。
 */
public enum PublishErrorCode {

    /* E1xxx — 模板语法与编译期 */
    E1001("标签名不存在"),
    E1002("参数未声明 / 类型不符 / 必填缺失"),
    E1003("块配对错误 / {cms:else/} 位置错"),
    E1004("字段名不在可用集合"),
    E1005("字段路径中间段断掉 / .count 用错"),
    E1006("include 越界 / 循环 / 超深"),
    E1007("模板编码不是 UTF-8 / 有 BOM"),

    /* E2xxx — 定义与查询参数 */
    E2001("内容保存校验失败"),
    E2002("层级内容成环"),
    E2003("SINGLE 类型已有内容"),
    E2004("主分类缺失或超过一个"),
    E2005("类型 code / 字段 code 用了保留名"),
    E2006("type / category / tag / author / code 指向不存在的对象"),
    E2007("where / orderby / relate 字段不可筛选 / 运算符非法 / 语法不可解析"),
    E2008("{cms:form} 的 code 不存在"),
    E2009("of='self'|'parent' 用在不成立的位置"),
    E2010("name 重复"),
    E2011("循环体内用了 name"),

    /* E3xxx — 页面结构 */
    E3001("分页主体数量错"),
    E3002("分页主体在 {cms:if} 体内"),
    E3003("{cms:pagelist} 用在正文分页上"),
    E3010("{cms:detail} 的 type 与页面 kind 不相容"),
    E3012("标签用在该页面类型上不允许的位置"),

    /* E4xxx — URL 与页面计划 */
    E4001("占位符不在白名单 / 必需占位符缺失 / {n} 用在不分页的页面"),
    E4002("模板查找失败"),
    E4003("会分页但没有 {n}"),
    E4004("URL 冲突"),
    E4005("筛选页数量超限"),
    E4006("产物路径过长"),

    /* W5xxx — 警告 */
    W5001("存在某页面类型的模板，但站点发布选项把它关掉了"),
    W5002("正文分页符落在块级元素内部"),
    W5003("引用的媒体派生文件不存在，已回退原图"),
    W5004("www/ 里的产物被人工改过"),
    W5005("站点选项 publish.keepReleases=0"),

    /* E6xxx — 文件与路径 */
    E6001("试图通过后台站点文件功能写 www/"),
    E6002("路径越界"),
    E6003("站点目录不存在 / 不可写"),

    /* E7xxx — H 类接口 */
    E7001("必填缺失"),
    E7002("格式错误"),
    E7003("选项非法"),
    E7004("附件类型 / 大小非法"),
    E7005("触发限流"),
    E7006("蜜罐命中"),
    E7007("验证码错误");

    private final String summary;

    PublishErrorCode(String summary) {
        this.summary = summary;
    }

    /** 该码的一句话含义（§10.2 的"触发"列），用于日志与后台列表。 */
    public String summary() {
        return summary;
    }

    /** 是否属于"警告"：不阻断发布，只进批次报告（§10.2 的 W5xxx）。 */
    public boolean warning() {
        return name().startsWith("W");
    }
}
