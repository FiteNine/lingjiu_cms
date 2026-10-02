package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * §4.5 第 11 条 ★：模板查找结果是否存在 → E4002（规则见 §7.3）。
 *
 * <p><b>期 1 只做到"判定函数"这一层</b>：候选清单与"先命中先用"的查找是纯函数（进页面种类与
 * 类型定义，出一个路径或 null），存在性判断由调用方以 {@link Predicate} 注入——期 2 的页面计划器
 * 传 {@code TemplateSource::exists}，测试传一个内存集合。因此本类不碰磁盘、不造 {@code PagePlan}。
 *
 * <p>三条写死的口径：
 * <ul>
 *   <li>候选清单**顺序即优先级**（§7.3 第（2）表的"先命中先用，逐级回退"），报错时按同一顺序
 *       列出全部找过的路径（§10.3 第 1 条：模板作者最需要的就是这个列表）；</li>
 *   <li>{@code search.html} / {@code 404.html} / {@code feed.xml} 三个找不到**不报错**
 *       （§7.3：用引擎内置的搜索页壳 / 默认 404 / 内置 feed 模板）；</li>
 *   <li>站点选项关掉的页面类型**不开工**（§7.3 第（3）条）：调用方先问
 *       {@link #disabledBySiteOptions}，关掉时既不查找也不报错。</li>
 * </ul>
 *
 * <p>类型定义里的 {@code detail_template} / {@code list_template} **优先于**主题默认规则（§7.3 第（1）条），
 * 因此它排在候选清单第一位。
 */
public final class TemplateLookupRules {

    private TemplateLookupRules() {
    }

    /** §7.3 第（2）表的每一行（{@code LIST} 因来源不同拆成三行，见 §7.2.1 v2.2）。 */
    public enum LookupKind {
        HOME, TYPE_LIST, CATEGORY_LIST, TAGPAGE, TAGLIST, ARCHIVE, FACET, DETAIL, SINGLE, SEARCH, STATIC, PAGE404, FEED
    }

    /**
     * 候选模板路径，**按查找顺序**。
     *
     * @param kind           页面种类
     * @param typeCode       内容类型 code（{@code {typeCode}_list.html} 这类候选要用）；没有时传 null
     * @param typeDef        类型定义（{@code detail_template} / {@code list_template} 优先）；没有时传 null
     * @param staticTemplate 站点选项 {@code pages.static} 条目里的 {@code template}（{@code STATIC} 用）
     */
    public static List<String> candidates(LookupKind kind, String typeCode, ContentTypeDef typeDef,
                                          String staticTemplate) {
        List<String> list = new ArrayList<>();
        String detailTemplate = typeDef == null ? null : typeDef.detailTemplate();
        String listTemplate = typeDef == null ? null : typeDef.listTemplate();
        switch (kind) {
            case HOME -> {
                list.add("index.html");
                list.add("home.html");
            }
            case TYPE_LIST -> {
                add(list, listTemplate);
                add(list, typeCode == null ? null : typeCode + "_list.html");
                list.add("list.html");
            }
            case CATEGORY_LIST -> {
                list.add("category_list.html");
                list.add("list.html");
            }
            case TAGPAGE -> {
                list.add("tag_list.html");
                add(list, typeCode == null ? null : typeCode + "_list.html");
                list.add("list.html");
            }
            case TAGLIST -> {
                list.add("tags.html");
                list.add("tag_list.html");
                list.add("list.html");
            }
            case ARCHIVE -> {
                list.add("archive_list.html");
                list.add("list.html");
            }
            case FACET -> {
                list.add("facet_list.html");
                add(list, typeCode == null ? null : typeCode + "_facet.html");
                add(list, typeCode == null ? null : typeCode + "_list.html");
                list.add("list.html");
            }
            case DETAIL -> {
                add(list, detailTemplate);
                add(list, typeCode == null ? null : typeCode + "_detail.html");
                list.add("detail.html");
            }
            case SINGLE -> {
                add(list, detailTemplate);
                add(list, typeCode == null ? null : typeCode + ".html");
                list.add("single.html");
                list.add("detail.html");
            }
            case SEARCH -> list.add("search.html");
            case STATIC -> add(list, staticTemplate);
            case PAGE404 -> list.add("404.html");
            case FEED -> list.add("feed.xml");
        }
        return List.copyOf(list);
    }

    /**
     * 先命中先用的查找。
     *
     * @param exists 该路径的模板是否存在（期 2 传 {@code TemplateSource::exists}）
     * @return 命中的路径；一个都没有时返回 null
     */
    public static String find(LookupKind kind, String typeCode, ContentTypeDef typeDef,
                              String staticTemplate, Predicate<String> exists) {
        for (String candidate : candidates(kind, typeCode, typeDef, staticTemplate)) {
            if (exists.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 查找并要求命中；一个都没有 → E4002（文案里列出**找过的全部路径**）。
     *
     * <p>三个"有内置兜底"的种类（{@code SEARCH} / {@code PAGE404} / {@code FEED}）找不到时返回 null
     * 而不报错：§7.3 明说它们缺模板不阻断整站发布。
     *
     * @param label 报错文案里怎么称呼这一页（例如"分类 news"、"类型 product"）；没有就传 null
     */
    public static String require(LookupKind kind, String typeCode, ContentTypeDef typeDef,
                                 String staticTemplate, String label, Predicate<String> exists) {
        List<String> tried = candidates(kind, typeCode, typeDef, staticTemplate);
        for (String candidate : tried) {
            if (exists.test(candidate)) {
                return candidate;
            }
        }
        if (kind == LookupKind.SEARCH || kind == LookupKind.PAGE404 || kind == LookupKind.FEED) {
            return null;
        }
        throw PublishException.error(PublishErrorCode.E4002,
                "页面类型 " + kind + (label == null || label.isBlank() ? "" : "（" + label + "）")
                        + "找不到模板",
                "依次找过 " + String.join("、", tried),
                tried.isEmpty()
                        ? "该页面类型没有候选模板，检查站点配置"
                        : "至少提供 " + tried.get(tried.size() - 1) + " 作为兜底（§7.3 的查找顺序）");
    }

    /**
     * 站点发布选项是否关掉了这一类页面（§7.3 第（3）条：{@code page.archive=0} 时不会去找
     * {@code archive_list.html}，也不会因为缺它而报错）。映射与 W5001 共用 {@link PageTypeOptions}。
     */
    public static boolean disabledBySiteOptions(PageType pageType, String typeCode, SiteConfig site) {
        return PageTypeOptions.disabled(pageType, typeCode, site);
    }

    private static void add(List<String> list, String candidate) {
        if (candidate != null && !candidate.isBlank() && !list.contains(candidate)) {
            list.add(candidate);
        }
    }
}
