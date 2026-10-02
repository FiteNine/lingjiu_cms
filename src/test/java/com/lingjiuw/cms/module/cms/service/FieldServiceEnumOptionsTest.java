package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.FieldSaveRequest;
import com.lingjiuw.cms.module.cms.entity.CmsContentType;
import com.lingjiuw.cms.module.cms.entity.CmsField;
import com.lingjiuw.cms.module.cms.mapper.CmsContentIndexMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTypeMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsFieldMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ENUM / ENUM_MULTI 的选项语法校验（值[:标签]，逗号分隔，§2.2）的纯单测：
 * 不连库，mapper 用 Mockito 替身；同时钉住 {@link ContentService#enumValues(String)} 的解析口径。
 */
class FieldServiceEnumOptionsTest {

    private final CmsFieldMapper fieldMapper = mock(CmsFieldMapper.class);
    private final CmsContentTypeMapper contentTypeMapper = mock(CmsContentTypeMapper.class);
    private final CmsContentIndexMapper contentIndexMapper = mock(CmsContentIndexMapper.class);
    private final FieldService service = new FieldService(fieldMapper, contentTypeMapper, contentIndexMapper);

    /** 纯单测里没有 MyBatis 运行时：LambdaQueryWrapper 取列名要靠 TableInfo 缓存 */
    @BeforeAll
    static void 初始化实体元数据() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CmsField.class);
        TableInfoHelper.initTableInfo(assistant, CmsContentType.class);
    }

    @BeforeEach
    void 准备站点与基础替身() {
        SiteContext.set(1L);
        when(contentTypeMapper.exists(any())).thenReturn(true);
        when(fieldMapper.exists(any())).thenReturn(false);
        when(fieldMapper.insert(any(CmsField.class))).thenReturn(1);
    }

    @AfterEach
    void 清理站点() {
        SiteContext.clear();
    }

    @Test
    void 合法选项通过() {
        service.create(request("ENUM", "red:红,blue:蓝"));
        verify(fieldMapper).insert(any(CmsField.class));
    }

    @Test
    void 请求里的类型名忽略大小写与空白() {
        BizException e = assertThrows(BizException.class,
                () -> service.create(request("  enum_multi  ", "red:红,red:红色")));
        assertEquals("选项值重复：red", e.getMessage());
        verify(fieldMapper, never()).insert(any(CmsField.class));
    }

    @Test
    void 全角冒号被拒() {
        BizException e = assertThrows(BizException.class,
                () -> service.create(request("ENUM", "red：红")));
        assertEquals("选项请使用半角冒号", e.getMessage());
        verify(fieldMapper, never()).insert(any(CmsField.class));
    }

    @Test
    void 重复值被拒() {
        BizException e = assertThrows(BizException.class,
                () -> service.create(request("ENUM", "red:红,red:红色")));
        assertEquals("选项值重复：red", e.getMessage());
        verify(fieldMapper, never()).insert(any(CmsField.class));
    }

    @Test
    void 首尾或连续逗号被拒() {
        BizException leading = assertThrows(BizException.class,
                () -> service.create(request("ENUM", ",red:红")));
        assertEquals("选项存在空项，请检查多余的逗号", leading.getMessage());
        BizException consecutive = assertThrows(BizException.class,
                () -> service.create(request("ENUM", "red:红,,blue:蓝")));
        assertEquals("选项存在空项，请检查多余的逗号", consecutive.getMessage());
        verify(fieldMapper, never()).insert(any(CmsField.class));
    }

    @Test
    void 空选项被拒() {
        BizException absent = assertThrows(BizException.class,
                () -> service.create(request("ENUM", null)));
        assertEquals("枚举类型必须配置选项（值[:标签]，逗号分隔）", absent.getMessage());
        BizException blank = assertThrows(BizException.class,
                () -> service.create(request("ENUM_MULTI", "   ")));
        assertEquals("枚举类型必须配置选项（值[:标签]，逗号分隔）", blank.getMessage());
        verify(fieldMapper, never()).insert(any(CmsField.class));
    }

    @Test
    void 冒号前没有值的选项被拒() {
        BizException e = assertThrows(BizException.class,
                () -> service.create(request("ENUM", ":红,blue:蓝")));
        assertEquals("存在没有值的选项", e.getMessage());
        verify(fieldMapper, never()).insert(any(CmsField.class));
    }

    @Test
    void 非枚举类型不校验选项() {
        service.create(request("TEXT", ",，：,,"));
        verify(fieldMapper).insert(any(CmsField.class));
    }

    @Test
    void update按最终生效的类型校验() {
        when(fieldMapper.selectOne(any())).thenReturn(existingField());

        BizException e = assertThrows(BizException.class,
                () -> service.update(9L, request("enum", "red:红,red:红色")));
        assertEquals("选项值重复：red", e.getMessage());
        verify(fieldMapper, never()).updateById(any(CmsField.class));
    }

    @Test
    void update合法选项通过() {
        when(fieldMapper.selectOne(any())).thenReturn(existingField());
        when(fieldMapper.updateById(any(CmsField.class))).thenReturn(1);

        service.update(9L, request("ENUM", "red:红"));
        verify(fieldMapper).updateById(any(CmsField.class));
    }

    @Test
    void 选项解析口径与原实现一致() {
        assertEquals(Set.of("red", "blue"), ContentService.enumValues("red:红, blue:蓝"));
        assertEquals(Set.of("red"), ContentService.enumValues("red:红,"));
        assertEquals(Set.of(), ContentService.enumValues(null));
        assertEquals(Set.of(), ContentService.enumValues(" , "));
        // 全角冒号不是分隔符：解析层保持原语义（整串算值），由校验层负责拦下
        assertEquals(Set.of("red：红"), ContentService.enumValues("red：红"));
    }

    private static FieldSaveRequest request(String fieldType, String options) {
        return new FieldSaveRequest("article", "color", "颜色", fieldType,
                null, 0, 0, null, options, 1, 1, 0, null, 0);
    }

    private static CmsField existingField() {
        CmsField field = new CmsField();
        field.setId(9L);
        field.setTypeCode("article");
        field.setCode("color");
        return field;
    }
}
