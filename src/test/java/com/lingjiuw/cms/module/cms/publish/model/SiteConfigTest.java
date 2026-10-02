package com.lingjiuw.cms.module.cms.publish.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code site} 作用域里的 {@code option} 开放前缀（模板里写 {@code [field:site.option.contact.wechat/]}）。
 *
 * <p>它解决的是一个很具体的缺口：站点级文案（微信号、二维码地址、主体名称、站长验证串…）
 * 没有别的地方可放——{@code site} 的其余 key 是封闭清单，加一个键就要改引擎；而这些值又常常
 * 在多个页面出现（页头 / 页脚 / 浮标 / 文档页），必须由一处渲染。
 */
class SiteConfigTest {

    private static SiteConfig site(Map<String, Object> options) {
        return new SiteConfig(1L, "lingjiuw", "凌久网", "www.lingjiuw.cn", "https",
                "/assets/img/logo.svg", "zh-CN", "技术解决方案", null, "站点描述", "粤ICP备1号",
                "176 7703 5288", "a@b.c", "lingjiuw.cn", null, "/assets/img/og-cover.png",
                "lingjiuw", null, null, options);
    }

    @Test
    void 点分选项拆成嵌套map() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("contact.wechat", "lingjiuw_cn");
        options.put("url.list", "/{categoryPath}/page-{n}/");
        options.put("page.category", "0");

        Object tree = site(options).toScope().get("option");

        Map<?, ?> root = assertInstanceOf(Map.class, tree);
        Map<?, ?> contact = assertInstanceOf(Map.class, root.get("contact"));
        assertEquals("lingjiuw_cn", contact.get("wechat"), "点分 key 要下钻到嵌套 Map 的叶子");
        Map<?, ?> url = assertInstanceOf(Map.class, root.get("url"));
        assertEquals("/{categoryPath}/page-{n}/", url.get("list"));
        Map<?, ?> page = assertInstanceOf(Map.class, root.get("page"));
        assertEquals("0", page.get("category"));
    }

    @Test
    void 结构化选项保持原样不转字符串() {
        // pages.static 这类 JSON 选项要按 List/Map 传下去：转成字符串以后模板再想取 .count / 下标就取不到了
        Object value = List.of(Map.of("code", "50x", "url", "/50x.html"));
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("pages.static", value);

        Map<?, ?> root = assertInstanceOf(Map.class, site(options).toScope().get("option"));
        Map<?, ?> pages = assertInstanceOf(Map.class, root.get("pages"));
        assertEquals(value, pages.get("static"));
    }

    @Test
    void 没有选项时给空表而不是null() {
        // 空表让 [field:site.option.x/] 走"缺值输出空串"这条路，而不是 NPE
        Object tree = site(Map.of()).toScope().get("option");
        assertTrue(assertInstanceOf(Map.class, tree).isEmpty());
    }

    @Test
    void 三段的选项名也只拆三层() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("brand.contact.wechat", "id");

        Map<?, ?> root = assertInstanceOf(Map.class, site(options).toScope().get("option"));
        Map<?, ?> brand = assertInstanceOf(Map.class, root.get("brand"));
        Map<?, ?> contact = assertInstanceOf(Map.class, brand.get("contact"));
        assertEquals("id", contact.get("wechat"));
    }
}
