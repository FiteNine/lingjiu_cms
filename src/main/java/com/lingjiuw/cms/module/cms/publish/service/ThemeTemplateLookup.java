package com.lingjiuw.cms.module.cms.publish.service;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.TemplateSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 模板查找（static-publish.md §7.3 的第（2）条"查找顺序"）。
 *
 * <p>"全部候选都不存在 → 计划期报错（E4002），文案里**列出找过的全部路径**"——这是模板作者
 * 最需要的一条诊断，因此这里不只返回 null，而是把找过的候选一并带出去。
 *
 * <p>{@code _} 前缀的文件不参与页面查找（{@code _partials/} 只作片段，§7.3 第（1）条）：
 * 候选名都是明写死的，因此这一条自然成立——但 {@code {typeCode}} 来自内容类型 code，
 * 而类型 code 受 §2.1 约束（小写字母数字下划线），不会以 {@code _} 开头。
 */
public final class ThemeTemplateLookup {

    private final TemplateSource source;
    private final String theme;

    public ThemeTemplateLookup(TemplateSource source, String theme) {
        this.source = source;
        this.theme = theme == null || theme.isBlank() ? "_default" : theme;
    }

    /** 查找命中的模板相对路径；全部候选都不存在 → null（调用方据此报 E4002 并列出候选）。 */
    public String find(PageType pageType, ContentTypeDef typeDef, String typeCode, String explicit) {
        List<String> candidates = candidates(pageType, typeDef, typeCode, explicit);
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && isSafeRelative(candidate)
                    && source.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 候选路径的第一道防线：{@code typeCode} 与 {@code explicit} 都来自数据库 / 站点配置，
     * 拼成路径前先拒绝 {@code ..}、绝对路径、盘符与反斜杠。
     *
     * <p>{@link com.lingjiuw.cms.module.cms.publish.service.FileTemplateSource} 内部还有一道
     * 规范化 + 主题根校验，但那是"最后一道"：这里拒掉，新的 {@code TemplateSource} 实现或新的
     * 调用方就不会把穿越形态透传下去（§7.8、§11.4 的路径边界）。
     */
    private static boolean isSafeRelative(String candidate) {
        return !candidate.startsWith("/") && candidate.indexOf('\\') < 0
                && !candidate.contains("..") && !candidate.matches("^[A-Za-z]:.*");
    }

    /** 找过的全部候选（报错文案要用）。{@code explicit} 是站点选项条目里写死的模板名。 */
    public List<String> candidates(PageType pageType, ContentTypeDef typeDef, String typeCode,
                                  String explicit) {
        List<String> list = new ArrayList<>();
        String typeDetailTemplate = typeDef == null ? null : typeDef.detailTemplate();
        String typeListTemplate = typeDef == null ? null : typeDef.listTemplate();
        switch (pageType) {
            case HOME -> {
                list.add("index.html");
                list.add("home.html");
            }
            case LIST -> {
                // 类型列表页：类型定义里的 list_template 优先（§7.3 第（1）条），再按类型 code
                list.add(typeListTemplate);
                if (typeCode != null) {
                    list.add(typeCode + "_list.html");
                }
                list.add("list.html");
            }
            case TAGLIST -> {
                list.add("tags.html");
                list.add("tag_list.html");
                list.add("list.html");
            }
            case TAGPAGE -> {
                list.add("tag_list.html");
                if (typeCode != null) {
                    list.add(typeCode + "_list.html");
                }
                list.add("list.html");
            }
            case ARCHIVE -> {
                list.add("archive_list.html");
                list.add("list.html");
            }
            case FACET -> {
                list.add("facet_list.html");
                if (typeCode != null) {
                    list.add(typeCode + "_facet.html");
                    list.add(typeCode + "_list.html");
                }
                list.add("list.html");
            }
            case DETAIL, DPAGE -> {
                list.add(typeDetailTemplate);
                if (typeCode != null) {
                    list.add(typeCode + "_detail.html");
                }
                list.add("detail.html");
            }
            case SINGLE -> {
                list.add(typeDetailTemplate);
                if (typeCode != null) {
                    list.add(typeCode + ".html");
                }
                list.add("single.html");
                list.add("detail.html");
            }
            case SEARCH -> list.add("search.html");
            case STATIC -> list.add(explicit);
            case PAGE404 -> list.add("404.html");
            case FEED -> list.add("feed.xml");
            // PageType 新增取值而这里没同步时，别静默返回空候选（find 返回 null、notFound 的文案
            // 变成"依次找过 "后面空着），直接在计划期暴露出来。
            default -> throw new IllegalStateException("页面类型缺少模板候选规则：" + pageType);
        }
        return list;
    }

    /** 找不到模板 → E4002，文案里列出找过的全部路径（§7.3 第（2）条）。 */
    public PublishException notFound(PageType pageType, List<String> candidates, String where) {
        List<String> tried = new ArrayList<>();
        for (String candidate : candidates) {
            tried.add(candidate == null || candidate.isBlank() ? "（站点选项里没写 template）" : candidate);
        }
        // pageType 要进文案：只看候选清单分不清"是哪种页面缺模板"，模板作者据此才知道该补哪一个
        return PublishException.error(PublishErrorCode.E4002, "找不到页面模板",
                null, 0,
                where + "（页面类型 " + (pageType == null ? "?" : pageType) + "）：在主题 " + theme
                        + " 下依次找过 " + String.join(" → ", tried),
                "在 sites/<站点>/template/" + theme + "/ 下补一个模板，或关掉该页面类型的发布选项（§7.3）");
    }

    public String theme() {
        return theme;
    }

    public TemplateSource source() {
        return source;
    }
}
