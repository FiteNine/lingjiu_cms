package com.lingjiuw.cms.module.cms.publish.nav;

import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.TestContentProvider.ContentSpec;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.EnumOption;
import com.lingjiuw.cms.module.cms.publish.model.FormDef;
import com.lingjiuw.cms.module.cms.publish.model.FormDef.FormField;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.tag.FormTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.args;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.page;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code {cms:form}}（static-publish.md §6.5）：规范 HTML 的五个部分、{@code hidden} 的 5 个
 * {@code self} 关键字（key 任意、只有值命中才替换、写错不报错）、{@code code} 不存在 → E2008。
 */
class FormTagTest {

    private final TestContentProvider provider = TestContentProvider.builder()
            .type(1, "product", "产品", ContentTypeDef.Kind.CONTENT)
            .content(ContentSpec.of(7, "product", "p-a", "产品\"A\"").publishTime("2026-05-01T10:00"))
            .form(inquiry())
            .build();

    private final FormTag tag = new FormTag(provider);

    private static FormDef inquiry() {
        return new FormDef("inquiry", "产品询价", "立即提交", "/thanks/", List.of(
                new FormField("name", "姓名", "text", true, "请输入姓名", List.of()),
                new FormField("email", "邮箱", "email", false, "you@example.com", List.of()),
                new FormField("message", "留言", "textarea", false, null, List.of()),
                new FormField("budget", "预算", "select", false, null,
                        List.of(new EnumOption("low", "1 万以内"), new EnumOption("high", "1 万以上"))),
                new FormField("resume", "简历", "file", false, null, List.of())));
    }

    private RenderContext productPage() {
        ContentItem entry = provider.contentById(7);
        RenderContext ctx = page(PageType.DETAIL, "product", entry, null);
        ctx.putPage("url", "/product/p-a/2.html");
        return ctx;
    }

    private String render(Map<String, String> args) {
        StringBuilder out = new StringBuilder();
        tag.render(NavTestSupport.tag("form", args), productPage(), new NavTestSupport.Recorder(), out);
        return out.toString();
    }

    @Test
    void 表单是自闭合标签() {
        assertFalse(tag.needsBody());
    }

    @Test
    void 渲染规范HTML的五个部分() {
        String html = render(args("code", "inquiry", "class", "inquiry"));

        // 1. form 本体
        assertTrue(html.contains("<form method=\"post\" action=\"/api/public/forms/inquiry\" "
                + "data-cms-form=\"inquiry\" class=\"inquiry\" enctype=\"multipart/form-data\">"), html);
        // 2. 字段控件来自表单定义（§9.5 的表）
        assertTrue(html.contains("<label for=\"name\">姓名</label>"), html);
        assertTrue(html.contains("<input type=\"text\" id=\"name\" name=\"name\" "
                + "placeholder=\"请输入姓名\" required>"), html);
        assertTrue(html.contains("<input type=\"email\" id=\"email\" name=\"email\" "
                + "placeholder=\"you@example.com\">"), html);
        assertTrue(html.contains("<textarea id=\"message\" name=\"message\" rows=\"5\"></textarea>"), html);
        assertTrue(html.contains("<select id=\"budget\" name=\"budget\">"), html);
        assertTrue(html.contains("<option value=\"low\">1 万以内</option>"), html);
        assertTrue(html.contains("<input type=\"file\" id=\"resume\" name=\"resume\">"), html);
        // 3. 蜜罐域（§9.9）
        assertTrue(html.contains("name=\"_hp\" tabindex=\"-1\" autocomplete=\"off\""), html);
        // 4. 站点 id 隐藏域（§11.1）
        assertTrue(html.contains("<input type=\"hidden\" name=\"siteId\" value=\"1\" data-cms-site=\"1\">"),
                html);
        // 5. 提交按钮（默认取表单定义）
        assertTrue(html.contains("<button type=\"submit\">立即提交</button>"), html);
        // 静态页里不含验证码（§6.5）
        assertFalse(html.contains("captcha"), html);
    }

    @Test
    void hidden里只有值命中self关键字才替换且key任意() {
        String html = render(args("code", "inquiry",
                "hidden", "utm_source:wechat,pid:contentId:self,whatever:not-a-keyword"));

        // key 完全任意：utm_source 与 whatever 都按字面量输出，且都不报错
        assertTrue(html.contains("<input type=\"hidden\" name=\"utm_source\" value=\"wechat\">"), html);
        assertTrue(html.contains("<input type=\"hidden\" name=\"whatever\" value=\"not-a-keyword\">"), html);
        // 值命中 contentId:self → 当前条目 id
        assertTrue(html.contains("<input type=\"hidden\" name=\"pid\" value=\"7\">"), html);
    }

    @Test
    void 五个self关键字的落地() {
        String html = render(args("code", "inquiry",
                "hidden", "contentId:self,contentType:self,contentUrl:self,contentTitle:self,pageUrl:self"));

        assertTrue(html.contains("name=\"contentId\" value=\"7\""), html);
        assertTrue(html.contains("name=\"contentType\" value=\"product\""), html);
        // 内容自己的 URL（不含分页号）与这一页的 URL（含分页号，§6.5 两个关键字的区别）
        assertTrue(html.contains("name=\"contentUrl\" value=\"/product/p-a.html\""), html);
        assertTrue(html.contains("name=\"pageUrl\" value=\"/product/p-a/2.html\""), html);
        // 标题走渲染器的转义出口（§5.2.1）
        assertTrue(html.contains("name=\"contentTitle\" value=\"产品&quot;A&quot;\""), html);
    }

    @Test
    void ajax为0时不给cmsJs挂载点() {
        assertTrue(render(args("code", "inquiry")).contains("data-cms-form=\"inquiry\""));
        assertFalse(render(args("code", "inquiry", "ajax", "0")).contains("data-cms-form"));
    }

    @Test
    void submitLabel参数覆盖表单定义() {
        assertTrue(render(args("code", "inquiry", "submitLabel", "马上询价"))
                .contains("<button type=\"submit\">马上询价</button>"));
    }

    @Test
    void 首页上没有当前条目时self关键字留空() {
        StringBuilder out = new StringBuilder();
        tag.render(NavTestSupport.tag("form", args("code", "inquiry", "hidden", "pid:contentId:self")),
                page(PageType.HOME, null), new NavTestSupport.Recorder(), out);

        assertTrue(out.toString().contains("<input type=\"hidden\" name=\"pid\" value=\"\">"), out.toString());
    }

    @Test
    void code不存在报E2008并列出本站表单() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(args("code", "inquir")));

        assertEquals(PublishErrorCode.E2008, error.code());
        assertTrue(error.getMessage().contains("[E2008] 表单 inquir 不存在"), error.getMessage());
        assertTrue(error.getMessage().contains("本站表单有 inquiry"), error.getMessage());
    }

    @Test
    void code缺失报E1002并列出参数() {
        PublishException error = assertThrows(PublishException.class, () -> render(Map.of()));

        assertEquals(PublishErrorCode.E1002, error.code());
        assertTrue(error.getMessage().contains("参数 code 不存在"), error.getMessage());
        assertTrue(error.getMessage().contains("{cms:form} 的参数有 code"), error.getMessage());
    }

    @Test
    void maxCount除feed外合法() {
        for (PageType pageType : PageType.values()) {
            assertEquals(pageType == PageType.FEED ? 0 : -1, tag.maxCount(pageType), pageType.name());
        }
    }

    @Test
    void feed上报E3012() {
        PublishException error = assertThrows(PublishException.class, () -> tag.render(
                NavTestSupport.tag("form", args("code", "inquiry")), page(PageType.FEED, null),
                new NavTestSupport.Recorder(), new StringBuilder()));

        assertEquals(PublishErrorCode.E3012, error.code());
    }
}
