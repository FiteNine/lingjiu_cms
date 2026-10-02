package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.entity.CmsMedia;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMediaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储。生产可扩展为 OSS（storage_type 字段已预留）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg", "ico",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "zip");

    /** 内容嗅探只读文件头这几十个字节就够；webp 需要看到偏移 8 处的 4 字节 */
    private static final int SNIFF_BYTES = 32;

    private final CmsMediaMapper mediaMapper;
    private final CmsContentMapper contentMapper;

    @Value("${cms.upload.dir}")
    private String uploadDir;

    @Value("${cms.upload.url-prefix}")
    private String urlPrefix;

    public PageResult<CmsMedia> page(long page, long size, String keyword) {
        return PageResult.of(mediaMapper.selectPage(new Page<>(page, size),
                Wrappers.<CmsMedia>lambdaQuery()
                        .eq(CmsMedia::getSiteId, SiteContext.siteId())
                        .like(StringUtils.hasText(keyword), CmsMedia::getName, keyword)
                        .orderByDesc(CmsMedia::getId)));
    }

    public CmsMedia upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "file";
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("不支持的文件类型：" + ext);
        }
        // 扩展名可以随便改，落盘前必须嗅探真实字节（M-7），否则伪装文件会被原样存储并公开访问
        verifyContent(file, ext);

        // 目录带 siteId（L-8）：不同站点不再共用同一份物理文件，一方删文件不会影响另一方
        Long siteId = SiteContext.siteId();
        String dir = siteId + "/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID() + "." + ext;
        Path target = Paths.get(uploadDir, dir, fileName);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toAbsolutePath());
        } catch (IOException e) {
            throw new BizException("文件保存失败：" + e.getMessage());
        }

        CmsMedia media = new CmsMedia();
        media.setSiteId(siteId);
        media.setName(originalName);
        media.setPath(dir + "/" + fileName);
        media.setUrl(urlPrefix + "/" + dir + "/" + fileName);
        media.setSize(file.getSize());
        media.setMimeType(file.getContentType());
        media.setExt(ext);
        media.setStorageType("LOCAL");
        try {
            mediaMapper.insert(media);
        } catch (RuntimeException e) {
            // 先落盘后写库：写库失败要把磁盘上的文件补偿删掉，否则留下没有记录的孤儿文件
            deleteQuietly(target);
            throw e;
        }
        return media;
    }

    public void delete(Long id) {
        CmsMedia media = mediaMapper.selectOne(Wrappers.<CmsMedia>lambdaQuery()
                .eq(CmsMedia::getId, id)
                .eq(CmsMedia::getSiteId, SiteContext.siteId()));
        if (media == null) {
            throw new BizException("文件不存在或已被删除");
        }
        // 内容自定义字段（IMAGES/FILES）存的是 url 字符串：引用没清就删库又删盘，内容侧会静默变死链（M-6）
        long refs = contentMapper.countContentRefsByUrl(SiteContext.siteId(), media.getUrl());
        if (refs > 0) {
            throw new BizException("该文件被 " + refs + " 条内容引用，删除会产生死链，请先调整内容后再删除");
        }
        // 删除语句自带站点条件：与上面的查询不是一对原子操作，用影响行数决定成败
        if (mediaMapper.delete(Wrappers.<CmsMedia>lambdaQuery()
                .eq(CmsMedia::getId, id)
                .eq(CmsMedia::getSiteId, SiteContext.siteId())) == 0) {
            throw new BizException("文件不存在或已被删除");
        }
        deleteQuietly(Paths.get(uploadDir, media.getPath()));
    }

    /** 删磁盘上的文件：失败只记日志，不影响数据库已经删掉的这一行 */
    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("本地文件删除失败: {} - {}", target, e.getMessage());
        }
    }

    /**
     * 魔数 / 结构嗅探：按扩展名读前若干字节并比对签名，不匹配直接拒绝。
     *
     * <p>MultipartFile 可重复读：这里只读文件头，随后的 {@code transferTo} 仍能拿到完整内容。
     * svg 不是二进制格式，按“BOM + 空白后的第一个标签”判断，大小写不敏感。
     */
    private void verifyContent(MultipartFile file, String ext) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(SNIFF_BYTES);
        } catch (IOException e) {
            throw new BizException("文件读取失败：" + e.getMessage());
        }
        boolean matched = switch (ext) {
            case "jpg", "jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47);
            case "gif" -> startsWithAscii(head, "GIF87a") || startsWithAscii(head, "GIF89a");
            case "webp" -> startsWithAscii(head, "RIFF") && asciiAt(head, 8, "WEBP");
            case "ico" -> startsWith(head, 0x00, 0x00, 0x01, 0x00);
            case "pdf" -> startsWithAscii(head, "%PDF");
            case "zip", "docx", "xlsx", "pptx" -> startsWith(head, 0x50, 0x4B, 0x03, 0x04);
            case "doc", "xls", "ppt" -> startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case "svg" -> looksLikeSvg(head);
            // txt / md 没有固定签名，只能跳过内容校验，靠响应头兜底
            default -> true;
        };
        if (!matched) {
            throw new BizException("文件内容与扩展名不符：" + ext);
        }
    }

    private static boolean startsWith(byte[] head, int... signature) {
        if (head.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] head, String signature) {
        return asciiAt(head, 0, signature);
    }

    private static boolean asciiAt(byte[] head, int offset, String text) {
        if (head.length < offset + text.length()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (head[offset + i] != (byte) text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** svg 允许 UTF-8 BOM 与前导空白，之后必须是 {@code <?xml} 或 {@code <svg}（忽略大小写） */
    private static boolean looksLikeSvg(byte[] head) {
        int start = 0;
        if (head.length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
            start = 3;
        }
        while (start < head.length && Character.isWhitespace(head[start] & 0xFF)) {
            start++;
        }
        String rest = new String(head, start, head.length - start, StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        return rest.startsWith("<?xml") || rest.startsWith("<svg");
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
