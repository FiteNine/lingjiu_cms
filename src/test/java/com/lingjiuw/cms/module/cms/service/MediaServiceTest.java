package com.lingjiuw.cms.module.cms.service;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.entity.CmsMedia;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMediaMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 媒体上传的内容嗅探（M-7）、按站点分目录（L-8）与删除前的引用检查（M-6）。
 * 纯单元测试：Mapper 用 Mockito 替身，上传目录指向 {@code @TempDir}，不依赖数据库与 Web 容器。
 */
class MediaServiceTest {

    private static final long SITE_ID = 7L;

    @TempDir
    Path uploadRoot;

    private CmsMediaMapper mediaMapper;
    private CmsContentMapper contentMapper;
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaMapper = mock(CmsMediaMapper.class);
        contentMapper = mock(CmsContentMapper.class);
        // 构造参数顺序与 MediaService 字段声明顺序一致（Lombok @RequiredArgsConstructor）
        mediaService = new MediaService(mediaMapper, contentMapper);
        ReflectionTestUtils.setField(mediaService, "uploadDir", uploadRoot.toString());
        ReflectionTestUtils.setField(mediaService, "urlPrefix", "/uploads");
        SiteContext.set(SITE_ID);
    }

    @AfterEach
    void tearDown() {
        SiteContext.clear();
    }

    @Test
    void pngWithRealSignatureIsAccepted() {
        when(mediaMapper.insert(any(CmsMedia.class))).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", pngHeader());

        CmsMedia media = mediaService.upload(file);

        // L-8：目录以 siteId 开头，物理路径与 URL 同步变化
        assertTrue(media.getPath().startsWith(SITE_ID + "/"), media.getPath());
        assertEquals("/uploads/" + media.getPath(), media.getUrl());
        assertTrue(Files.exists(uploadRoot.resolve(media.getPath())));
    }

    @Test
    void textDisguisedAsPngIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                "这不是图片".getBytes(StandardCharsets.UTF_8));

        BizException e = assertThrows(BizException.class, () -> mediaService.upload(file));

        assertEquals("文件内容与扩展名不符：png", e.getMessage());
        verify(mediaMapper, never()).insert(any(CmsMedia.class));
    }

    @Test
    void svgStartingWithXmlDeclarationIsAccepted() {
        when(mediaMapper.insert(any(CmsMedia.class))).thenReturn(1);
        String svg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>";
        MockMultipartFile file = new MockMultipartFile("file", "icon.svg", "image/svg+xml",
                svg.getBytes(StandardCharsets.UTF_8));

        CmsMedia media = mediaService.upload(file);

        assertEquals("svg", media.getExt());
    }

    @Test
    void htmlDisguisedAsSvgIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "evil.svg", "image/svg+xml",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        BizException e = assertThrows(BizException.class, () -> mediaService.upload(file));

        assertEquals("文件内容与扩展名不符：svg", e.getMessage());
        verify(mediaMapper, never()).insert(any(CmsMedia.class));
    }

    @Test
    void deleteIsRejectedWhileContentStillReferencesTheUrl() {
        CmsMedia media = new CmsMedia();
        media.setId(1L);
        media.setSiteId(SITE_ID);
        media.setUrl("/uploads/7/2026/10/used.png");
        when(mediaMapper.selectOne(any())).thenReturn(media);
        when(contentMapper.countContentRefsByUrl(SITE_ID, media.getUrl())).thenReturn(2L);

        BizException e = assertThrows(BizException.class, () -> mediaService.delete(1L));

        assertEquals("该文件被 2 条内容引用，删除会产生死链，请先调整内容后再删除", e.getMessage());
        verify(mediaMapper, never()).delete(any());
    }

    private static byte[] pngHeader() {
        // PNG 魔数 89 50 4E 47，补上换行等字节保证文件非空
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }
}
