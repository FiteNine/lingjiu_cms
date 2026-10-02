package com.lingjiuw.cms.common.site;

import com.lingjiuw.cms.common.exception.BizException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 站点目录的路径边界：把「相对路径 → 站点根目录内的绝对路径」这件事收在一处，供后台文件管理
 * （{@code SiteService} / {@code SiteFileService}）与发布引擎共用（static-publish.md §11.4）。
 *
 * <p>三个既有方法（{@link #resolveUnder} / {@link #relative} / {@link #checkName}）的规则与错误文案
 * 与 {@code SiteService} 原实现逐条一致；加严的检查各自独立成方法，互不影响既有调用方的行为：
 * <ul>
 *   <li>{@link #resolveUnder}：另拒 URL 编码绕过（{@code %2e%2e%2f}），与「{@code ..} 上跳」是同一条规则；</li>
 *   <li>{@link #assertNoSymlink}：{@code root → target} 的每一段都查符号链接（引擎禁止 `www/` 树内有软链）；</li>
 *   <li>{@link #assertWritable}：发布前置检查，避免渲染完几万页才发现目录不可写；</li>
 *   <li>{@link #checkSegment} / {@link #assertPathLength}：Windows 保留名与超长路径。{@link #checkName}
 *       被后台文件管理器共用，行为保持不变，加严的部分只在新方法里生效。</li>
 * </ul>
 */
public final class SitePathBoundary {

    /** 文件/文件夹名：排除各平台的路径分隔符与文件名保留字符 */
    private static final Pattern DIR_NAME = Pattern.compile("[^\\\\/:*?\"<>|]{1,64}");

    /** Windows 保留设备名（大小写不敏感）：不能作为文件或文件夹名 */
    private static final Set<String> RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    /** 路径长度上限：Windows 传统 MAX_PATH，超了写文件才会失败 */
    private static final int MAX_PATH_LENGTH = 260;

    private SitePathBoundary() {
    }

    /**
     * 把相对路径解析成站点根目录内的绝对路径：绝对路径（含 Windows 盘符）、{@code ..} 上跳与
     * URL 编码的 {@code .} {@code /} {@code \} 一律拒绝。根目录内的符号链接不在此拦截——
     * 目录浏览用 {@code NOFOLLOW_LINKS} 过滤，按路径写入才需要 {@link #assertNoSymlink}。
     */
    public static Path resolveUnder(Path root, String relativePath) {
        String value = relativePath == null ? "" : relativePath.trim().replace('\\', '/');
        if (value.startsWith("/") || value.matches("^[A-Za-z]:.*")) {
            throw new BizException("请使用站点根目录下的相对路径");
        }
        assertNoEncodedSeparator(value);
        Path target;
        try {
            target = value.isEmpty() ? root : root.resolve(value).normalize();
        } catch (InvalidPathException e) {
            throw new BizException("[E6002] 路径越界：" + value);
        }
        if (!target.startsWith(root)) {
            throw new BizException("路径超出站点根目录范围");
        }
        return target;
    }

    /** 相对路径展示用（一律用 {@code /} 分隔，与前端路径风格一致） */
    public static String relative(Path root, Path target) {
        // Path.relativize 对不在 root 下的目标会抛 IllegalArgumentException（会被兜底成 500），
        // 这里与 resolveUnder / assertNoSymlink 保持一致，统一转成带错误码的 BizException
        if (!target.startsWith(root)) {
            throw new BizException("[E6002] 路径越界：" + target + " 不在站点根目录内");
        }
        return root.relativize(target).toString().replace('\\', '/');
    }

    /**
     * 校验文件/文件夹名：trim 后必须是一个路径段（不含分隔符与文件名保留字符），返回校验过的名字。
     * 站点目录选择器与 {@code SiteFileService} 新建站点文件共用这一条规则。
     */
    public static String checkName(String name, String what) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || ".".equals(value) || "..".equals(value) || !DIR_NAME.matcher(value).matches()) {
            throw new BizException(what + "名称不合法：不能包含 \\ / : * ? \" < > | 字符，最长 64 字符");
        }
        return value;
    }

    /**
     * 比 {@link #checkName} 多拦 Windows 保留名（{@code CON} / {@code NUL} 这类设备名）。发布引擎
     * 生成文件名、媒体落盘时用它；后台文件管理器沿用 {@link #checkName}，行为不变。
     */
    public static String checkSegment(String name, String what) {
        String value = checkName(name, what);
        String base = value.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (RESERVED_NAMES.contains(base)) {
            throw new BizException("[E6002] 路径越界：" + what + "名称是 Windows 保留名：" + value);
        }
        return value;
    }

    /**
     * 逐段检查 {@code root → target} 上是否存在符号链接，命中即拒绝（{@code www/} 树内禁止符号链接，
     * 否则引擎会顺着链接写到站点目录之外）。{@code root} 自身不算——站点根目录挂在别处是部署选择。
     * 不存在的段不算命中，所以"待新建的路径"同样可以先过这一关。
     *
     * <p>注意：本方法是「先检查、后写盘」，只保证检查那一刻的结果；检查与真正落盘之间目录被换成
     * 符号链接的竞态（TOCTOU）它挡不住。调用方要么保证这段时间内路径不可变，要么在写盘时用
     * {@code LinkOption.NOFOLLOW_LINKS}/写盘后基于 {@code toRealPath()} 做二次校验。
     */
    public static void assertNoSymlink(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new BizException("[E6002] 路径越界：" + target);
        }
        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new BizException("[E6002] 路径越界：路径段是符号链接：" + current);
            }
        }
    }

    /** 发布前置检查：目录不存在或不可写直接拒绝（E6003），别等渲染完才发现写不进去 */
    public static void assertWritable(Path path) {
        if (!Files.isDirectory(path)) {
            throw new BizException("[E6003] 站点目录不存在：" + path + "（检查属主与权限）");
        }
        if (!Files.isWritable(path)) {
            throw new BizException("[E6003] 站点目录不可写：" + path + "（检查属主与权限）");
        }
    }

    /** 路径长度上限检查：绝对路径超过 {@value #MAX_PATH_LENGTH} 字符时写盘必然失败，提前拒绝 */
    public static void assertPathLength(Path path) {
        int length = path.toString().length();
        if (length > MAX_PATH_LENGTH) {
            throw new BizException("[E6002] 路径越界：路径长度 " + length + " 字符，超过 " + MAX_PATH_LENGTH + " 上限：" + path);
        }
    }

    /** URL 编码绕过：解码后会变成 {@code .} {@code /} {@code \} 的转义一律拒绝（§12.3 第 4 条） */
    private static void assertNoEncodedSeparator(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")) {
            throw new BizException("[E6002] 路径越界：" + value + "（路径里不能出现 %2e / %2f / %5c 这类 URL 编码）");
        }
    }
}
