package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import java.util.List;
import java.util.Map;

/**
 * §12.3 第 1 条：编译期校验的**表驱动**用例。
 *
 * <p>每一行是"触发模板 + 期望错误码 + 文案关键字"，{@code code == null} 表示"这一行必须编译通过"
 * （每条 ★ 校验都至少各有一个失败用例与一个通过用例）。
 */
abstract class CompileCaseSupport {

    /** 一行用例。 */
    record Case(String name, PageType pageType, String typeCode, String template,
                PublishErrorCode code, String... keywords) {

        static Case ok(String name, PageType pageType, String typeCode, String template) {
            return new Case(name, pageType, typeCode, template, null);
        }

        static Case fail(String name, PageType pageType, String typeCode, String template,
                         PublishErrorCode code, String... keywords) {
            return new Case(name, pageType, typeCode, template, code, keywords);
        }
    }

    /** 站点基线：四个类型 + 两个表单；product 带 5 个自定义字段（含 1 个未索引的）。 */
    static CompileFixture base(PageType pageType, String typeCode) {
        return new CompileFixture()
                .type(product())
                .type("book", ContentTypeDef.Kind.TREE)
                .type("about", ContentTypeDef.Kind.SINGLE)
                .type("author", ContentTypeDef.Kind.CONTENT)
                .form("inquiry")
                .form("subscribe")
                .page(pageType, typeCode);
    }

    static CompileFixture base(Case item) {
        return base(item.pageType(), item.typeCode());
    }

    /** 产品类型：brand 已索引、price 未索引、images/specs 是多值。 */
    static ContentTypeDef product() {
        return product(null);
    }

    /** 产品类型，可指定 {@code paginate_body}（正文分页）。 */
    static ContentTypeDef product(String paginateBody) {
        return new ContentTypeDef(1L, "product", "产品", ContentTypeDef.Kind.CONTENT, false,
                "/product/{slug}.html", "/product/", null, null, paginateBody,
                "publishTime", "desc", 20, null, null,
                List.of(FieldDef.indexed("product", "brand", FieldType.TEXT),
                        FieldDef.of("product", "price", FieldType.DECIMAL),
                        FieldDef.of("product", "images", FieldType.IMAGES),
                        FieldDef.of("product", "specs", FieldType.JSON),
                        FieldDef.indexed("product", "accessories", FieldType.RELATION),
                        FieldDef.of("product", "colors", FieldType.ENUM_MULTI)),
                Map.of());
    }

    /** 跑一行用例：期望错误码为 null 时断言编译通过，否则断言错误码与文案关键字。 */
    static void run(Case item) {
        CompileFixture fixture = base(item);
        fixture.file(entry(item.pageType()), item.template());
        if (item.code() == null) {
            TemplateAst ast = fixture.compile(entry(item.pageType()));
            org.junit.jupiter.api.Assertions.assertNotNull(ast, item.name());
            return;
        }
        PublishException error = fixture.error(entry(item.pageType()));
        CompileFixture.assertError(error, item.code(), item.keywords());
    }

    /** 入口模板路径：按页面类型给一个像样的名字，报错文案里的位置才读得通。 */
    static String entry(PageType pageType) {
        return switch (pageType) {
            case HOME -> "index.html";
            case LIST, TAGLIST -> "list.html";
            case TAGPAGE -> "tag_list.html";
            case ARCHIVE -> "archive_list.html";
            case FACET -> "facet_list.html";
            case DETAIL, DPAGE -> "product_detail.html";
            case SINGLE -> "about.html";
            case SEARCH -> "search.html";
            case STATIC -> "thanks.html";
            case PAGE404 -> "404.html";
            case FEED -> "feed.xml";
        };
    }
}
