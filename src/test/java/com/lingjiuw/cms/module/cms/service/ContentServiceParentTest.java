package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.security.LoginUser;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.ContentSaveRequest;
import com.lingjiuw.cms.module.cms.entity.CmsContent;
import com.lingjiuw.cms.module.cms.entity.CmsContentCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContentIndex;
import com.lingjiuw.cms.module.cms.entity.CmsContentTag;
import com.lingjiuw.cms.module.cms.entity.CmsContentType;
import com.lingjiuw.cms.module.cms.entity.CmsField;
import com.lingjiuw.cms.module.cms.mapper.CmsCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentIndexMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTagMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTypeMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsFieldMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsTagMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内容树父子关系校验（§2.3 的层级内容）的纯单测：不连库，mapper 用 Mockito 替身。
 * 覆盖父级不存在 / 跨类型 / 选自身 / 选自身后代 / 有子内容未删的拒绝，以及根父级的放行。
 */
class ContentServiceParentTest {

    private final CmsContentMapper contentMapper = mock(CmsContentMapper.class);
    private final CmsContentTypeMapper contentTypeMapper = mock(CmsContentTypeMapper.class);
    private final CmsFieldMapper fieldMapper = mock(CmsFieldMapper.class);
    private final CmsContentIndexMapper contentIndexMapper = mock(CmsContentIndexMapper.class);
    private final CmsContentCategoryMapper contentCategoryMapper = mock(CmsContentCategoryMapper.class);
    private final CmsContentTagMapper contentTagMapper = mock(CmsContentTagMapper.class);
    private final CmsCategoryMapper categoryMapper = mock(CmsCategoryMapper.class);
    private final CmsTagMapper tagMapper = mock(CmsTagMapper.class);
    private final ContentService service = new ContentService(contentMapper, contentTypeMapper, fieldMapper,
            contentIndexMapper, contentCategoryMapper, contentTagMapper, categoryMapper, tagMapper);

    /** 纯单测里没有 MyBatis 运行时：LambdaQueryWrapper 取列名要靠 TableInfo 缓存 */
    @BeforeAll
    static void 初始化实体元数据() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CmsContent.class);
        TableInfoHelper.initTableInfo(assistant, CmsContentType.class);
        TableInfoHelper.initTableInfo(assistant, CmsField.class);
        TableInfoHelper.initTableInfo(assistant, CmsContentIndex.class);
        TableInfoHelper.initTableInfo(assistant, CmsContentCategory.class);
        TableInfoHelper.initTableInfo(assistant, CmsContentTag.class);
    }

    @BeforeEach
    void 准备站点与基础替身() {
        SiteContext.set(1L);
        // 保存内容要记录 create_by / update_by，走真实的 SecurityUtils 路径
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                LoginUser.builder().id(1L).username("tester").build(), null));
        when(fieldMapper.selectList(any())).thenReturn(List.of());
        CmsContentType type = new CmsContentType();
        type.setCode("cat");
        type.setName("栏目");
        type.setKind("TREE");
        when(contentTypeMapper.selectOne(any())).thenReturn(type);
    }

    @AfterEach
    void 清理站点() {
        SiteContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void create父级为根或负数直接放行() {
        service.create(request("cat", -1L));
        verify(contentMapper, never()).selectOne(any());
        verify(contentMapper).insertContent(any(CmsContent.class));
    }

    @Test
    void create父级为空视为根() {
        service.create(request("cat", null));
        verify(contentMapper, never()).selectOne(any());
        verify(contentMapper).insertContent(any(CmsContent.class));
    }

    @Test
    void create父级不存在被拒() {
        when(contentMapper.selectOne(any())).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.create(request("cat", 6L)));
        assertEquals("父级内容不存在或已被删除", e.getMessage());
        verify(contentMapper, never()).insertContent(any(CmsContent.class));
    }

    @Test
    void create父级类型不一致被拒() {
        when(contentMapper.selectOne(any())).thenReturn(content(6L, "book", 0L));

        BizException e = assertThrows(BizException.class, () -> service.create(request("cat", 6L)));
        assertEquals("父级内容必须与当前内容属于同一类型", e.getMessage());
        verify(contentMapper, never()).insertContent(any(CmsContent.class));
    }

    @Test
    void create合法父级通过() {
        when(contentMapper.selectOne(any())).thenReturn(content(6L, "cat", 0L));

        service.create(request("cat", 6L));
        verify(contentMapper).insertContent(any(CmsContent.class));
    }

    @Test
    void update父级不能选择自身() {
        when(contentMapper.selectOne(any())).thenReturn(content(5L, "cat", 0L));

        BizException e = assertThrows(BizException.class,
                () -> service.update(5L, request("cat", 5L)));
        assertEquals("父级内容不能选择自身", e.getMessage());
        verify(contentMapper, never()).updateContent(any(CmsContent.class));
    }

    @Test
    void update父级不能选择自己的下级() {
        when(contentMapper.selectOne(any())).thenReturn(
                content(5L, "cat", 0L), content(6L, "cat", 7L), content(7L, "cat", 5L));

        BizException e = assertThrows(BizException.class,
                () -> service.update(5L, request("cat", 6L)));
        assertEquals("父级内容不能选择自己的下级", e.getMessage());
        verify(contentMapper, never()).updateContent(any(CmsContent.class));
    }

    @Test
    void update合法父级通过() {
        when(contentMapper.selectOne(any())).thenReturn(
                content(5L, "cat", 0L), content(6L, "cat", 0L));

        service.update(5L, request("cat", 6L));
        verify(contentMapper).updateContent(any(CmsContent.class));
    }

    @Test
    void delete有子内容被拒() {
        when(contentMapper.selectOne(any())).thenReturn(content(5L, "cat", 0L));
        when(contentMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.delete(5L));
        assertEquals("存在子内容，不能删除", e.getMessage());
        verify(contentMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete无子内容通过() {
        when(contentMapper.selectOne(any())).thenReturn(content(5L, "cat", 0L));
        when(contentMapper.selectCount(any())).thenReturn(0L);

        service.delete(5L);
        verify(contentMapper).deleteById(5L);
    }

    private static CmsContent content(Long id, String typeCode, Long parentId) {
        CmsContent content = new CmsContent();
        content.setId(id);
        content.setTypeCode(typeCode);
        content.setParentId(parentId);
        return content;
    }

    private static ContentSaveRequest request(String typeCode, Long parentId) {
        return new ContentSaveRequest(typeCode, parentId, null, "标题", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }
}
