package com.lingjiuw.cms.module.cms.service;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.common.site.SitePathBoundary;
import com.lingjiuw.cms.module.cms.dto.SiteFileContent;
import com.lingjiuw.cms.module.cms.dto.SiteFileListing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 站点目录（当前站点的网站文件目录）的文件浏览 / 读取 / 保存 / 新建 / 删除。
 *
 * <p>活动范围就是这一个站点目录：路径解析复用 {@link SitePathBoundary#resolveUnder}，绝对路径与 {@code ..}
 * 上跳一律拒绝，符号链接不跟随，所以翻不出当前站点。文件类型只按扩展名判断：图片预览（转成
 * data URL 给前端）、文本编辑保存、其余类型只看文件信息；没有扩展名的文件按其余类型处理。
 */
@Service
@RequiredArgsConstructor
public class SiteFileService {

    /** 可在线编辑的文本后缀（不含点）；.gitignore 这类点开头的文件同样按最后一个点取后缀 */
    private static final Set<String> TEXT_EXT = Set.of(
            "html", "htm", "xhtml", "xml", "svg", "vue", "js", "mjs", "cjs", "jsx", "ts", "tsx",
            "css", "scss", "sass", "less", "json", "jsonc", "yml", "yaml", "toml", "ini", "conf",
            "cfg", "properties", "env", "txt", "md", "markdown", "csv", "log", "sql", "sh", "bash",
            "bat", "cmd", "ps1", "java", "kt", "py", "rb", "go", "rs", "php", "cs", "c", "h",
            "cpp", "hpp", "tpl", "ftl", "vm", "ejs", "hbs", "mustache", "gitignore", "editorconfig",
            "htaccess");

    /** 可预览的图片后缀 → data URL 的 MIME */
    private static final Map<String, String> IMAGE_MIME = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp",
            "bmp", "image/bmp",
            "ico", "image/x-icon",
            "avif", "image/avif");

    private static final String KIND_TEXT = "TEXT";
    private static final String KIND_IMAGE = "IMAGE";
    private static final String KIND_OTHER = "OTHER";

    /** 文本读写上限：超过就不往页面里塞，避免浏览器卡死 */
    private static final long MAX_TEXT_SIZE = 2L * 1024 * 1024;

    /** 图片预览上限：内容以 base64 一次性返回 */
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    private final SiteService siteService;

    /**
     * 列出当前站点目录下某个路径的直接子目录与文件。站点还没保存过、目录尚不存在时返回空列表，
     * 页面看到的是「目录为空」，不会一进来就报错。
     */
    public SiteFileListing list(String path) {
        Path base = siteService.siteDir(SiteContext.siteId());
        Path dir = SitePathBoundary.resolveUnder(base, path);
        // NOFOLLOW_LINKS 只保护最后一段：路径中间的软链要在这里拒绝，否则会列出站点目录之外的内容
        SitePathBoundary.assertNoSymlink(base, dir);
        if (!Files.isDirectory(dir)) {
            if (dir.equals(base)) {
                return new SiteFileListing(base.toString(), "", List.of(), List.of());
            }
            throw new BizException("目录不存在：" + SitePathBoundary.relative(base, dir));
        }
        List<SiteFileListing.DirNode> dirs = new ArrayList<>();
        List<SiteFileListing.FileNode> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path item : stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                String name = item.getFileName().toString();
                String itemPath = SitePathBoundary.relative(base, item);
                if (Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS)) {
                    dirs.add(new SiteFileListing.DirNode(name, itemPath));
                } else if (Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(new SiteFileListing.FileNode(name, itemPath, Files.size(item), kindOf(name)));
                }
            }
        } catch (IOException e) {
            throw new BizException("读取目录失败：" + e.getMessage());
        }
        return new SiteFileListing(base.toString(), SitePathBoundary.relative(base, dir), dirs, files);
    }

    /** 读取当前站点目录下的一个文件 */
    public SiteFileContent read(String path) {
        Path base = siteService.siteDir(SiteContext.siteId());
        Path file = SitePathBoundary.resolveUnder(base, path);
        String rel = SitePathBoundary.relative(base, file);
        SitePathBoundary.assertNoSymlink(base, file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new BizException(rel.isEmpty() ? "请选择一个文件" : "文件不存在：" + rel);
        }
        String name = file.getFileName().toString();
        String kind = kindOf(name);
        try {
            long size = Files.size(file);
            if (KIND_IMAGE.equals(kind)) {
                if (size > MAX_IMAGE_SIZE) {
                    throw new BizException("图片超过 5MB，暂不支持预览：" + rel);
                }
                String dataUrl = "data:" + IMAGE_MIME.get(extOf(name)) + ";base64,"
                        + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
                return new SiteFileContent(rel, name, size, kind, null, dataUrl);
            }
            if (KIND_TEXT.equals(kind)) {
                if (size > MAX_TEXT_SIZE) {
                    throw new BizException("文件超过 2MB，暂不支持在线编辑：" + rel);
                }
                return new SiteFileContent(rel, name, size, kind, readText(file), null);
            }
            return new SiteFileContent(rel, name, size, kind, null, null);
        } catch (IOException e) {
            throw new BizException("读取文件失败：" + e.getMessage());
        }
    }

    /** 保存当前站点目录下的文本文件：只覆盖已存在的文本文件，不会新建文件 */
    public void save(String path, String content) {
        Path base = siteService.siteDir(SiteContext.siteId());
        Path file = SitePathBoundary.resolveUnder(base, path);
        String rel = SitePathBoundary.relative(base, file);
        SitePathBoundary.assertNoSymlink(base, file);
        if (rel.isEmpty() || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new BizException("文件不存在或已被删除，请刷新后重试");
        }
        if (!KIND_TEXT.equals(kindOf(file.getFileName().toString()))) {
            throw new BizException("只能保存文本文件：" + rel);
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_SIZE) {
            throw new BizException("内容超过 2MB，保存失败：" + rel);
        }
        try {
            // 与读取同一把尺子：读不出 UTF-8 的文件不给覆盖，免得把原文件写坏
            readText(file);
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException("保存失败：" + e.getMessage());
        }
    }

    /**
     * 在当前站点目录下新建一个空文件（parent 留空表示站点目录本身），返回相对站点目录的路径。
     * 只建空文件，内容由页面接着走 {@link #save} 保存。
     */
    public String createFile(String parent, String name) {
        Path base = siteService.siteDir(SiteContext.siteId());
        Path file = parentDir(base, parent).resolve(SitePathBoundary.checkName(name, "文件"));
        try {
            Files.createFile(file);
        } catch (FileAlreadyExistsException e) {
            throw new BizException("同名文件或文件夹已存在：" + SitePathBoundary.relative(base, file));
        } catch (IOException e) {
            throw new BizException("新建文件失败：" + e.getMessage());
        }
        return SitePathBoundary.relative(base, file);
    }

    /** 在当前站点目录下新建一个文件夹，返回相对站点目录的路径 */
    public String createDir(String parent, String name) {
        Path base = siteService.siteDir(SiteContext.siteId());
        Path dir = parentDir(base, parent).resolve(SitePathBoundary.checkName(name, "文件夹"));
        try {
            Files.createDirectory(dir);
        } catch (FileAlreadyExistsException e) {
            throw new BizException("同名文件或文件夹已存在：" + SitePathBoundary.relative(base, dir));
        } catch (IOException e) {
            throw new BizException("新建文件夹失败：" + e.getMessage());
        }
        return SitePathBoundary.relative(base, dir);
    }

    /**
     * 删除当前站点目录下的一个文件或空文件夹。站点目录本身不能删；非空文件夹要求先清空里面的内容——
     * 一次点击删掉整棵目录树不可逆，与「站点下有内容不给删」是同一把尺子。
     */
    public void delete(String path) {
        Path base = siteService.siteDir(SiteContext.siteId());
        Path target = SitePathBoundary.resolveUnder(base, path);
        String rel = SitePathBoundary.relative(base, target);
        if (rel.isEmpty()) {
            throw new BizException("请选择要删除的文件或文件夹");
        }
        // 路径中间的软链要先拒绝：delete 会顺着链接删到站点目录之外
        SitePathBoundary.assertNoSymlink(base, target);
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            requireEmpty(target, rel);
        } else if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new BizException("文件不存在或已被删除，请刷新后重试：" + rel);
        }
        try {
            Files.delete(target);
        } catch (IOException e) {
            throw new BizException("删除失败：" + e.getMessage());
        }
    }

    /** 新建的上级目录：必须是站点目录里已存在的目录，否则页面上的树已经过期 */
    private static Path parentDir(Path base, String parent) {
        Path dir = SitePathBoundary.resolveUnder(base, parent);
        // createFile / createDir 都从这里进：父目录是软链时新文件会建到站点目录之外
        SitePathBoundary.assertNoSymlink(base, dir);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw new BizException("上级目录不存在，请刷新后重试");
        }
        return dir;
    }

    private static void requireEmpty(Path dir, String rel) {
        try (Stream<Path> stream = Files.list(dir)) {
            if (stream.findAny().isPresent()) {
                throw new BizException("文件夹不为空，请先删除里面的内容：" + rel);
            }
        } catch (IOException e) {
            throw new BizException("读取目录失败：" + e.getMessage());
        }
    }

    /** 一律按 UTF-8 读写；非 UTF-8 的文件直接拒绝，免得读出来是乱码、存回去把原文件毁掉 */
    private static String readText(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            throw new BizException("文件不是 UTF-8 编码，暂不支持在线编辑：" + file.getFileName());
        }
    }

    /** 按扩展名判断文件类型；没有后缀或不在清单里的一律按不支持处理 */
    private static String kindOf(String name) {
        String ext = extOf(name);
        if (IMAGE_MIME.containsKey(ext)) {
            return KIND_IMAGE;
        }
        return TEXT_EXT.contains(ext) ? KIND_TEXT : KIND_OTHER;
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
