package com.lingjiuw.cms.module.cms.publish.service;

import com.lingjiuw.cms.module.cms.publish.template.TemplateSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * 主题模板的磁盘读取（static-publish.md §4.4、§5.3、§7.3）。
 *
 * <p>这是 {@link TemplateSource} 的期 2 实现：主题根目录由页面计划给出
 * （{@code sites/<站点>/template/<主题名>/}，§7.3），编译器只认相对路径。
 *
 * <p>三道边界都在这里，一处不漏：
 * <ul>
 *   <li><b>路径边界</b>：解析后的真实路径必须仍在主题根之内（{@link com.lingjiuw.cms.common.site.SitePathBoundary}
 *       的规则由编译器先在语法层拦一道，这里再按规范化后的绝对路径兜一道）；</li>
 *   <li><b>不跟随符号链接</b>：符号链接会让"主题目录内"这个前提失效；</li>
 *   <li><b>只读</b>：引擎从不写模板目录（§7.8 的 {@code template/} 边界）。</li>
 * </ul>
 */
public final class FileTemplateSource implements TemplateSource {

    private final Path themeRoot;

    public FileTemplateSource(Path themeRoot) {
        this.themeRoot = themeRoot.toAbsolutePath().normalize();
    }

    public Path themeRoot() {
        return themeRoot;
    }

    @Override
    public Template load(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path file = themeRoot.resolve(relativePath.replace('\\', '/')).normalize();
        if (!file.startsWith(themeRoot)) {
            return null;
        }
        // 末段的 NOFOLLOW 检查：路径本身就是符号链接时直接拒绝
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            // 只查最后一段是不够的：主题目录里若有一个符号链接子目录（themeRoot/link -> /etc），
            // themeRoot/link/passwd 经 normalize 后仍以 themeRoot 为前缀，而末段是普通文件。
            // 因此解析真实路径后再比一次主题根（§7.8 的路径边界）。
            Path real = file.toRealPath();
            if (!real.startsWith(themeRoot.toRealPath())) {
                return null;
            }
            // 内容与 size 必须出自**同一次**读取：Template.fingerprint() 把 size 算进 astVersion
            // （§4.4），Files.size 是另一次系统调用，期间文件被替换会让"内容变则指纹变"失效。
            byte[] bytes = Files.readAllBytes(real);
            return new Template(relativePath, new String(bytes, StandardCharsets.UTF_8),
                    Files.getLastModifiedTime(real).toMillis(), bytes.length);
        } catch (IOException e) {
            throw new UncheckedIOException("读模板失败：" + file, e);
        }
    }
}
