package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.DefVersion;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 编译上下文（static-publish.md §4.4、§4.5）。
 *
 * <p>编译器与校验器只通过它看世界：**该页面类型 + 该类型定义下，模板能用哪些字段**。
 * §4.5 第 4/5/6/7 条校验、{@code {cms:if}} 的字段名、以及报错文案里的"可用字段有 …"
 * 全部读这里——"报错列出的字段清单由引擎按『该页面类型 + 该类型定义』现算，
 * 因此字段定义一改，报错内容立刻跟着变"（§5.1 原话）。
 *
 * <p><b>上下文签名</b>（§4.4）：{@code pageType + typeCode + 具名作用域存在性}，
 * 例如 {@code DETAIL:product:site,channel,page,item}。同一个模板文件会被多种页面共用
 * （{@code list.html} 是兜底模板、{@code single.html} 被多个 {@code SINGLE} 类型共用），
 * 只按路径缓存会造成两种实现分歧：一种按并集编译（漏报 E1004），一种按页面类型各编译
 * （语义与 key 对不上）。{@code query} 与 {@code param} **不进签名**：它们由模板自己决定，
 * 不是页面上下文的一部分。
 */
public final class CompileContext {

    private final long siteId;
    private final PageType pageType;
    private final String typeCode;
    private final ContentProvider provider;
    private final DefVersion defVersion;
    private final String signature;

    /**
     * @param siteId   站点 id
     * @param pageType 页面类型（决定 {@code channel} / {@code item} 是否存在，§5.1 第（4）条）
     * @param typeCode 当前页面的内容类型 code；首页 / 标签页等没有时为 null
     * @param provider 取数出口，用于算"该类型有哪些字段"
     */
    public CompileContext(long siteId, PageType pageType, String typeCode, ContentProvider provider) {
        this(siteId, pageType, typeCode, provider, DefVersion.ZERO);
    }

    /**
     * @param defVersion 类型 / 字段 / 站点配置的定义版本号，进编译缓存的 key（§4.4）——
     *                   只按 {@code mtime + size} 失效时，改了字段定义而模板没动，
     *                   缓存仍命中用旧定义编译出的 AST，§4.5 的第 4/5/6 条校验全部失效
     */
    public CompileContext(long siteId, PageType pageType, String typeCode, ContentProvider provider,
                          DefVersion defVersion) {
        this.siteId = siteId;
        this.pageType = pageType;
        this.typeCode = typeCode;
        this.provider = provider;
        this.defVersion = defVersion == null ? DefVersion.ZERO : defVersion;
        this.signature = signatureOf(pageType, typeCode);
    }

    public long siteId() {
        return siteId;
    }

    public PageType pageType() {
        return pageType;
    }

    public String typeCode() {
        return typeCode;
    }

    public ContentProvider provider() {
        return provider;
    }

    /** 定义版本号（§4.4）：编译缓存 key 的一部分。 */
    public DefVersion defVersion() {
        return defVersion;
    }

    /** 编译缓存的上下文签名（§4.4）；同路径不同上下文是两条缓存记录。 */
    public String signature() {
        return signature;
    }

    private static String signatureOf(PageType pageType, String typeCode) {
        List<String> scopes = new java.util.ArrayList<>();
        for (String name : RenderContext.RESERVED_NAMES) {
            boolean exists = switch (name) {
                case "site", "page" -> true;
                case "channel" -> pageType.hasChannel();
                case "item" -> pageType.hasCurrentEntry();
                default -> false;
            };
            if (exists) {
                scopes.add(name);
            }
        }
        return pageType + ":" + (typeCode == null ? "-" : typeCode) + ":" + String.join(",", scopes);
    }

    /* ---------------- 可用的字段集合 ---------------- */

    /**
     * 某个内容类型的迭代项/当前条目有哪些字段：
     * **内置字段 + 派生字段 + 正文字段 + 该类型的全部自定义字段**（§2.2、§6.3 的统一产出契约）。
     *
     * @param code 类型 code；{@code null} / {@code all} / 不存在的类型 → {@link BuiltinFields#CROSS_TYPE}
     *             （{@code type='all'} 时自定义字段一律不可用，§6.3 v2.2 定死）
     */
    public Set<String> fieldsOfType(String code) {
        if (code == null || "all".equals(code)) {
            return BuiltinFields.CROSS_TYPE;
        }
        ContentTypeDef def = provider.type(code);
        if (def == null) {
            return BuiltinFields.CROSS_TYPE;
        }
        Set<String> fields = new LinkedHashSet<>(BuiltinFields.CROSS_TYPE);
        for (FieldDef field : def.fields()) {
            fields.add(field.code());
        }
        return Set.copyOf(fields);
    }

    /** 本页面当前条目的可用字段；页面没有当前条目时返回空集。 */
    public Set<String> currentEntryFields() {
        return pageType.hasCurrentEntry() ? fieldsOfType(typeCode) : Set.of();
    }

    /** 匿名栈底的可用字段（= 当前条目字段）；报错文案里的"可用字段有 …"用它。 */
    public Set<String> anonymousFields() {
        return currentEntryFields();
    }

    /** 当前页面是否有"当前条目"（§5.1 第（1）条）：只有详情页 / 单页有。 */
    public boolean hasCurrentEntry() {
        return pageType.hasCurrentEntry();
    }

    /** 当前页面的内容类型是否层级类型（决定 {@code of='parent'} 是否可能成立，§4.5 第 14 条）。 */
    public boolean currentTypeHierarchical() {
        ContentTypeDef def = typeCode == null ? null : provider.type(typeCode);
        return def != null && (def.hierarchical() || def.kind() == ContentTypeDef.Kind.TREE);
    }

    /**
     * {@code {cms:foreach}} 迭代项的字段（§3.5 裁定五、§6.2）。
     *
     * @param source     迭代来源的字段名或预加载名（{@code images} / {@code toc} / {@code tags} /
     *                   {@code specs} / {@code ancestors} / {@code categories} / {@code site.alternates}…）
     * @param ownerType  该字段所属内容项的类型 code；{@code children} / {@code related} 需要它才能递归
     * @return 迭代项的 key 集合；未知来源返回空集（调用方报 E1004）
     */
    public Set<String> iteratorKeys(String source, String ownerType) {
        if (source == null) {
            return Set.of();
        }
        String code = source.startsWith("site.") ? source.substring("site.".length()) : source;
        return switch (code) {
            case "images" -> Set.of("url", "alt", "title", "width", "height", "name", "size", "mime",
                    "index", "index0", "isFirst", "isLast", "count");
            case "files" -> Set.of("url", "name", "ext", "size", "mime",
                    "index", "index0", "isFirst", "isLast", "count");
            case "tags" -> Set.of("id", "name", "slug", "url", "count",
                    "index", "index0", "isFirst", "isLast");
            case "specs" -> Set.of("key", "value", "index", "index0", "isFirst", "isLast");
            case "toc" -> Set.of("level", "text", "id", "url", "index", "index0", "isFirst", "isLast");
            case "ancestors" -> Set.of("id", "title", "url", "typeCode",
                    "index", "index0", "isFirst", "isLast");
            case "categories" -> Set.of("id", "name", "slug", "url", "dimension",
                    "index", "index0", "isFirst", "isLast");
            case "alternates" -> Set.of("lang", "url", "current",
                    "index", "index0", "isFirst", "isLast");
            case "children", "related" -> fieldsOfType(ownerType);
            default -> Set.of();
        };
    }

    /**
     * 具名作用域里可用的 key（§5.1 第（2）条的清单）。作用域在当前页面类型上**不存在**时返回空集，
     * 调用方据此报 E1004 并说明"该页面类型上没有这个作用域"。
     *
     * @param scope 6 个保留名之一
     */
    public Set<String> namedScopeKeys(String scope) {
        return switch (scope) {
            case "site" -> BuiltinFields.SITE_KEYS;
            case "page" -> BuiltinFields.PAGE_KEYS;
            case "query" -> BuiltinFields.QUERY_KEYS;
            case "channel" -> channelKeys();
            case "item" -> currentEntryFields();
            // param 的 key 由 include 的字面量参数与 §7.2.3 的 4 个注入 key 决定，编译期无法穷举，
            // 因此这里是"可能存在的 key"，校验只查保留名冲突而不查具体 key（§5.3）。
            case "param" -> BuiltinFields.INJECTED_PARAM_KEYS;
            default -> Set.of();
        };
    }

    /**
     * {@code channel} 作用域在当前页面上可用的 key。
     *
     * <p>在 {@link BuiltinFields#channelKeys} 的基础上补两个**引擎实际会产出**的 key，
     * 否则"模板按契约写、引擎自己报错"：
     * <ul>
     *   <li>{@code count} —— 归档页与类型列表页的 {@code channel} 也有它
     *       （页面计划填的是 {@code archives()} / 类型条目给的计数）；</li>
     *   <li>{@code typeCode} —— 类型列表页的 {@code channel} 是**内容类型本身**
     *       （{@code SitePlanner.typeChannel}），它带 {@code typeCode} 而不带 {@code path}。</li>
     * </ul>
     */
    private Set<String> channelKeys() {
        if (!pageType.hasChannel()) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>(BuiltinFields.channelKeys(pageType, null));
        keys.add("count");
        // 只有**类型列表页**的 channel 才是内容类型本身（SitePlanner.typeChannel，带 typeCode）；
        // 归档页的 channel 只有 year/month/label/url/count，放行 typeCode 会让模板按不存在的契约写
        if (pageType.matrixColumn() == PageType.LIST) {
            keys.add("typeCode");
        }
        if (pageType == PageType.ARCHIVE) {
            keys.add("year");
            keys.add("month");
        }
        return Set.copyOf(keys);
    }

    /** 具名作用域在当前页面类型上是否存在（§5.1 第（4）条的表）。 */
    public boolean namedScopeExists(String scope) {
        return switch (scope) {
            case "site", "page" -> true;
            case "channel" -> pageType.hasChannel();
            case "item" -> pageType.hasCurrentEntry();
            case "param", "query" -> true;
            default -> false;
        };
    }

    /* ---------------- 报错文案里的清单 ---------------- */

    /** 本站点全部类型 code，按字典序；E2006 / E1004 的"→ 建议"用它列清单。 */
    public String describeTypes() {
        Set<String> codes = new TreeSet<>();
        for (ContentTypeDef def : provider.types()) {
            codes.add(def.code());
        }
        return codes.isEmpty() ? "（本站还没有定义任何内容类型）" : String.join(" ", codes);
    }

    /** 可用的字段清单，按字典序；报错文案用它。 */
    public static String describe(Set<String> fields) {
        return fields.isEmpty() ? "（一个都没有）" : String.join(" ", new TreeSet<>(fields));
    }
}
