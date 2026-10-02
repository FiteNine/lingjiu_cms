package com.lingjiuw.cms.module.cms.publish.service;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SitePathBoundary;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 产物写盘（static-publish.md §7.8、§8.5、§8.6）。
 *
 * <p>三条边界写在这里，别处不重复：
 * <ul>
 *   <li><b>只写 {@code www/}</b>：{@code data/}（人工资产）、{@code template/}（主题）、
 *       {@code release/}（批次副本）一个字节都不碰（§7.8："{@code www/} 与 {@code data/} 的边界
 *       就是 GC 的边界"）；</li>
 *   <li><b>单页原子写</b>：同目录临时文件 → fsync → rename 覆盖（§8.5）。目录不存在先建；</li>
 *   <li><b>UTF-8 无 BOM、{@code \n}、文件末尾保留换行</b>（§7.8）——产物要能进 git diff。</li>
 * </ul>
 *
 * <p>路径长度上限（§7.8 的 200 字符）在写盘前查，超限报 E4006 并跳过该页，
 * 而不是等到 Windows 上写失败才发现。
 */
@Slf4j
public final class ArtifactWriter {

    /** 单条产物相对路径的长度上限（§7.8）。 */
    private static final int MAX_RELATIVE_PATH = 200;

    /** 临时文件名的自增后缀（配合 pid 保证唯一）。 */
    private static final AtomicLong TEMP_SEQ = new AtomicLong();

    private final Path wwwRoot;

    public ArtifactWriter(Path wwwRoot) {
        this.wwwRoot = wwwRoot.toAbsolutePath().normalize();
    }

    public Path root() {
        return wwwRoot;
    }

    /** 建目录（幂等）；{@code www/} 本身也在这里补出来。 */
    public void prepare() {
        try {
            Files.createDirectories(wwwRoot);
        } catch (IOException e) {
            throw new BizException("[E6003] 站点产物目录不可用：" + wwwRoot + "（" + e.getMessage() + "）");
        }
        SitePathBoundary.assertWritable(wwwRoot);
    }

    /**
     * 原子写一个文本产物。
     *
     * @param relativePath {@code www/} 之下的相对路径（{@code /} 分隔）
     * @return 写入的字节数
     */
    public long write(String relativePath, String content) {
        assertRelative(relativePath);
        Path target = resolve(relativePath);
        byte[] bytes = normalize(content).getBytes(StandardCharsets.UTF_8);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            // 临时文件与目标同目录：跨文件系统的 rename 不是原子的（§8.5）。
            // 文件名必须**唯一**：固定的"<目标名>.tmp"在并发发布（同站点多线程 / 多实例）下会
            // 互相覆盖，再把半成品 move 到目标上。用 resolveSibling 换掉文件名，不会让路径变长。
            temp = target.resolveSibling(".publish-" + ProcessHandle.current().pid() + "-"
                    + TEMP_SEQ.incrementAndGet() + ".tmp");
            Files.write(temp, bytes);
            // §8.5：写同目录临时文件 → fsync → rename。少了 force(true)，崩溃时可能 rename 出空文件。
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("写产物失败：{}（{}）", relativePath, e.getMessage());
            throw new BizException("[E6003] 写产物失败：" + relativePath + "（" + e.getMessage() + "）");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // move 成功后临时文件已经不在了；万一残留，listArtifacts 的 .tmp 过滤会挡掉它
                }
            }
        }
        return bytes.length;
    }

    /**
     * 删除一个产物（GC 专用，§8.6）。只允许删 {@code www/} 之内的文件。
     *
     * @return 真的删掉了返回 true
     */
    public boolean delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        Path target = resolve(relativePath);
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("删除产物失败：{}（{}）", relativePath, e.getMessage());
            return false;
        }
    }

    /** 自底向上删除空目录，止于 {@code www/}（§8.6：空目录要清，但别把 {@code www/} 本身删了）。 */
    public void pruneEmptyDirectories() {
        try (var stream = Files.walk(wwwRoot)) {
            List<Path> dirs = stream.filter(Files::isDirectory).sorted(java.util.Comparator.reverseOrder())
                    .toList();
            for (Path dir : dirs) {
                if (dir.equals(wwwRoot)) {
                    continue;
                }
                try (var children = Files.list(dir)) {
                    if (children.findAny().isEmpty()) {
                        Files.deleteIfExists(dir);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("清理空目录失败：{}", e.getMessage());
        }
    }

    /** {@code www/} 之下已有的全部文件（相对路径，{@code /} 分隔），按路径排序。 */
    public List<String> listArtifacts() {
        if (!Files.isDirectory(wwwRoot)) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        try (var stream = Files.walk(wwwRoot)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> SitePathBoundary.relative(wwwRoot, path))
                    .filter(name -> !name.endsWith(".tmp"))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException e) {
            log.warn("列产物失败：{}", e.getMessage());
        }
        return files;
    }

    /** 产物的最后修改时间（毫秒）；不存在返回 0。{@code W5004} 的"被人工改过"判定用它。 */
    public long lastModified(String relativePath) {
        Path target = resolve(relativePath);
        try {
            return Files.getLastModifiedTime(target).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** 把主题的 {@code assets/} 复制到 {@code www/assets/}（§7.2.2 的"主题资源"行）。 */
    public int copyAssets(Path themeAssets) {
        if (themeAssets == null || !Files.isDirectory(themeAssets)) {
            return 0;
        }
        int copied = 0;
        try (var stream = Files.walk(themeAssets)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(
                    path -> !Files.isSymbolicLink(path)).toList()) {
                String relative = SitePathBoundary.relative(themeAssets, file);
                // 与 copyStatic 同口径：产物路径长度超限要先报 E4006，别等 Windows 上写盘失败
                assertRelative("assets/" + relative);
                Path target = resolve("assets/" + relative);
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
        } catch (IOException e) {
            // 吞掉异常 = 静默产出"没有样式表"的残缺站点，调用方（批次报告）什么都看不到
            log.warn("复制主题资源失败：{}（{}）", themeAssets, e.getMessage());
            throw new BizException("[E6003] 复制主题资源失败：" + themeAssets + "（" + e.getMessage() + "）");
        }
        return copied;
    }

    /**
     * 把主题的 {@code static/} 复制到 {@code www/} **根**，返回写出的相对路径清单
     * （调用方要把它登记进本批次产物，否则下次 GC 会当成"上一批多出来的文件"删掉）。
     *
     * <p>为什么不并进 {@code assets/}：根目录下有几个位置是**协议或工具约定死**的——
     * 浏览器与爬虫直接探测 {@code /favicon.ico}，搜索引擎要 {@code /robots.txt}、
     * {@code /.well-known/} 下的验证文件。这些文件必须落在根上，放到 {@code assets/} 下探测不到。
     *
     * <p>只复制普通文件、跳过符号链接：主题来自磁盘，跟链接会把边界外的文件带进产物。
     */
    public List<String> copyStatic(Path themeStatic) {
        if (themeStatic == null || !Files.isDirectory(themeStatic)) {
            return List.of();
        }
        List<String> written = new ArrayList<>();
        try (var stream = Files.walk(themeStatic)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(
                    path -> !Files.isSymbolicLink(path)).toList()) {
                String relative = SitePathBoundary.relative(themeStatic, file);
                assertRelative(relative);
                Path target = resolve(relative);
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                written.add(relative);
            }
        } catch (IOException e) {
            log.warn("复制主题静态文件失败：{}（{}）", themeStatic, e.getMessage());
            throw new BizException("[E6003] 复制主题静态文件失败：" + themeStatic + "（"
                    + e.getMessage() + "）");
        }
        written.sort(String::compareTo);
        return written;
    }

    /** 解析 {@code www/} 之内的绝对路径；越界抛 E6002（{@code ..}、盘符、URL 编码都在这里被拦住）。 */
    public Path resolve(String relativePath) {
        Path target = SitePathBoundary.resolveUnder(wwwRoot, relativePath);
        SitePathBoundary.assertNoSymlink(wwwRoot, target);
        return target;
    }

    /** 单条产物相对路径的长度上限（§7.8）：超限报 E4006，前缀 {@code /} 不计。 */
    public static void assertPathLength(String relativePath) {
        if (relativePath != null && relativePath.length() > MAX_RELATIVE_PATH) {
            throw new BizException("[E4006] 产物路径过长（" + relativePath.length() + " > "
                    + MAX_RELATIVE_PATH + "）：" + relativePath);
        }
    }

    private void assertRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BizException("[E4002] 产物路径是空的");
        }
        assertPathLength(relativePath);
    }

    /**
     * 产物编码约定（§7.8）：UTF-8 **无 BOM**、`\n`、末尾保留一个换行。
     *
     * <p>只补末尾换行是不够的：模板或片段里带 BOM、用 CRLF 行尾（Windows 编辑器常见）时会被
     * 原样写进产物，破坏"同一份输入产出同一份字节"（git diff、增量 hash、字节一致性校验都受影响）。
     */
    static String normalize(String content) {
        String text = content == null ? "" : content;
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        if (!text.endsWith("\n")) {
            text = text + "\n";
        }
        return text;
    }
}
