package com.lingjiuw.cms.module.ai.copilot.tool.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 模板路径归一化：AI 会给出两种写法——主题内相对路径（{@code index.html}）与
 * 站点目录里的完整路径（{@code template/mint/index.html}，即 cms_site_file_list 看到的那种）。
 * 后者不剥前缀会被拼成 {@code template/mint/template/mint/index.html}，报"模板不存在"。
 */
class TemplateToolProviderTest {

    @Test
    void 主题内相对路径原样保留() {
        assertEquals("index.html", TemplateToolProvider.themeRelative("index.html", "mint"));
        assertEquals("_partials/header.html",
                TemplateToolProvider.themeRelative("_partials/header.html", "mint"));
    }

    @Test
    void 剥掉template与主题前缀() {
        assertEquals("index.html", TemplateToolProvider.themeRelative("template/mint/index.html", "mint"));
        assertEquals("_partials/header.html",
                TemplateToolProvider.themeRelative("template/mint/_partials/header.html", "mint"));
        assertEquals("index.html", TemplateToolProvider.themeRelative("/template/mint/index.html", "mint"));
    }

    @Test
    void 只剥template加主题这一整段前缀不误伤同名目录() {
        // 主题里真有一个叫 template 的子目录时不能被剥掉
        assertEquals("template/x.html", TemplateToolProvider.themeRelative("template/x.html", "mint"));
        // 别的主题名不能当自己的前缀剥
        assertEquals("other/index.html", TemplateToolProvider.themeRelative("other/index.html", "mint"));
        assertEquals("template/other/index.html",
                TemplateToolProvider.themeRelative("template/other/index.html", "mint"));
    }

    @Test
    void 反斜杠与空白被归一() {
        assertEquals("index.html", TemplateToolProvider.themeRelative("  template\\mint\\index.html  ", "mint"));
    }
}
