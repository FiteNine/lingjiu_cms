package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.entity.CmsMedia;
import com.lingjiuw.cms.module.cms.mapper.CmsMediaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    private final CmsMediaMapper mediaMapper;

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

        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID() + "." + ext;
        Path target = Paths.get(uploadDir, dir, fileName);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toAbsolutePath());
        } catch (IOException e) {
            throw new BizException("文件保存失败：" + e.getMessage());
        }

        CmsMedia media = new CmsMedia();
        media.setSiteId(SiteContext.siteId());
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

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
