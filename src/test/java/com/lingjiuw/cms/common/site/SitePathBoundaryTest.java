package com.lingjiuw.cms.common.site;

import com.lingjiuw.cms.common.exception.BizException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路径边界用例（static-publish.md §12.3 第 4 条 / §11.4）：{@code ..}、绝对路径、盘符、
 * URL 编码绕过、符号链接、长路径、Windows 保留名，外加"加严不能误伤正常路径"。
 */
class SitePathBoundaryTest {

    @TempDir
    Path root;

    /** 越界路径：解析必须抛异常，且文案命中关键内容 */
    static Stream<Arguments> escapingPaths() {
        return Stream.of(
                // 1. .. 上跳
                Arguments.of("../etc/passwd", "路径超出站点根目录范围"),
                Arguments.of("data/../../etc/passwd", "路径超出站点根目录范围"),
                Arguments.of("..", "路径超出站点根目录范围"),
                // 2. 绝对路径（含 UNC）
                Arguments.of("/etc/passwd", "请使用站点根目录下的相对路径"),
                Arguments.of("//server/share/x.html", "请使用站点根目录下的相对路径"),
                // 3. Windows 盘符
                Arguments.of("C:/Windows/System32/drivers/etc/hosts", "请使用站点根目录下的相对路径"),
                Arguments.of("c:\\windows\\win.ini", "请使用站点根目录下的相对路径"),
                // 4. URL 编码绕过：解码后会变成 . / \，一律拒绝
                Arguments.of("%2e%2e%2fetc/passwd", "[E6002]"),
                Arguments.of("data/%2e%2e/%2e%2e/etc", "[E6002]"),
                Arguments.of("data%2f..%2f..%2fetc", "[E6002]"),
                Arguments.of("data%5C..%5Cwindows", "[E6002]"),
                Arguments.of("%2E%2E%2Fetc", "[E6002]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("escapingPaths")
    void 越界路径被拒绝(String path, String expectedMessage) {
        BizException e = assertThrows(BizException.class, () -> SitePathBoundary.resolveUnder(root, path));
        assertTrue(e.getMessage().contains(expectedMessage), e.getMessage());
    }

    /** 正常路径必须照旧通过：加严的检查不能误伤站点目录里的真实文件 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"data/a.html", "template/news/list.html", "www/index.html"})
    void 正常路径照旧通过(String path) {
        Path target = SitePathBoundary.resolveUnder(root, path);
        assertTrue(target.startsWith(root), target.toString());
        assertEquals(path, SitePathBoundary.relative(root, target));
    }

    @Test
    void 段内上跳不出界时照旧通过() {
        Path target = SitePathBoundary.resolveUnder(root, "data/../template/x.html");
        assertEquals("template/x.html", SitePathBoundary.relative(root, target));
    }

    @Test
    void 空路径解析成站点根目录() {
        assertEquals(root, SitePathBoundary.resolveUnder(root, null));
        assertEquals(root, SitePathBoundary.resolveUnder(root, ""));
        assertEquals(root, SitePathBoundary.resolveUnder(root, "  "));
    }

    @Test
    void 反斜杠按分隔符处理并统一用斜杠展示() {
        Path target = SitePathBoundary.resolveUnder(root, "data\\news\\a.html");
        assertEquals("data/news/a.html", SitePathBoundary.relative(root, target));
    }

    /** 6. 长路径：超过 260 字符的绝对路径写盘必然失败，提前拒绝 */
    @Test
    void 超长路径被拒绝() {
        Path longPath = SitePathBoundary.resolveUnder(root, "a".repeat(300) + ".html");
        BizException e = assertThrows(BizException.class, () -> SitePathBoundary.assertPathLength(longPath));
        assertTrue(e.getMessage().startsWith("[E6002]"), e.getMessage());
        assertTrue(e.getMessage().contains("260"), e.getMessage());

        assertDoesNotThrow(() -> SitePathBoundary.assertPathLength(root.resolve("data/news/list.html")));
    }

    /** 7. Windows 保留名：加严的 checkSegment 拒绝，共用给后台文件管理器的 checkName 行为不变 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"CON", "con", "NUL", "nul.html", "AUX", "COM1", "lpt9.txt", "PRN"})
    void 保留名被拒绝(String name) {
        BizException e = assertThrows(BizException.class, () -> SitePathBoundary.checkSegment(name, "文件"));
        assertTrue(e.getMessage().startsWith("[E6002]"), e.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"data", "index.html", "console.html", "my.nul", "template-1"})
    void 保留名之外的名字照旧通过(String name) {
        assertEquals(name, SitePathBoundary.checkSegment(name, "文件"));
    }

    @Test
    void checkName的既有行为不变() {
        assertEquals("NUL", SitePathBoundary.checkName("NUL", "文件"));
        assertEquals("index.html", SitePathBoundary.checkName("  index.html  ", "文件"));

        for (String bad : new String[]{"", ".", "..", "a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "x".repeat(65)}) {
            BizException e = assertThrows(BizException.class, () -> SitePathBoundary.checkName(bad, "文件"), bad);
            assertTrue(e.getMessage().contains("名称不合法"), e.getMessage());
        }
    }

    /** 5. 符号链接：root → target 的每一段都要拦（Windows 上建软链可能没权限，建不出就跳过） */
    @Test
    void 符号链接段被拒绝() throws IOException {
        Files.createDirectories(root.resolve("data"));
        Files.createDirectories(root.resolve("template"));
        Path link = root.resolve("data").resolve("link");
        try {
            Files.createSymbolicLink(link, root.resolve("template"));
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // Windows 未开开发者模式 / 无权限时建不出软链：跳过（用假软链测等于没测）
            Assumptions.abort("当前环境建不出符号链接，跳过该用例: " + e.getMessage());
        }

        BizException e = assertThrows(BizException.class,
                () -> SitePathBoundary.assertNoSymlink(root, link.resolve("x.html")));
        assertTrue(e.getMessage().startsWith("[E6002]"), e.getMessage());
    }

    @Test
    void 不存在的路径段不算符号链接() {
        // 引擎写的是"还不存在"的产物文件，先查符号链接再落盘是常规顺序
        assertDoesNotThrow(() -> SitePathBoundary.assertNoSymlink(root, root.resolve("www/news/a/b.html")));
        assertDoesNotThrow(() -> SitePathBoundary.assertNoSymlink(root, root));
    }

    @Test
    void 不在站点根目录内的目标被拒绝() {
        BizException e = assertThrows(BizException.class,
                () -> SitePathBoundary.assertNoSymlink(root, root.getParent().resolve("elsewhere")));
        assertTrue(e.getMessage().startsWith("[E6002]"), e.getMessage());
    }

    @Test
    void 目录不存在或不可写时拒绝() {
        BizException missing = assertThrows(BizException.class,
                () -> SitePathBoundary.assertWritable(root.resolve("nope")));
        assertTrue(missing.getMessage().startsWith("[E6003]"), missing.getMessage());

        assertDoesNotThrow(() -> SitePathBoundary.assertWritable(root));
    }
}
