package com.lingjiuw.cms.module.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.cms.entity.CmsContentIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 字段索引表：删除走 BaseMapper 的逻辑删除，重建的批量写走 XML。
 *
 * <p>唯一索引是 {@code (content_id, field_code, value_key) where deleted = 0} 的部分索引，
 * 因此"先逻辑删旧行、再插新行"的顺序不能颠倒（同一事务内）。
 */
@Mapper
public interface CmsContentIndexMapper extends BaseMapper<CmsContentIndex> {

    /**
     * 批量写入索引行。
     *
     * <p>前置条件：{@code rows} 非空且同一批内 {@code (fieldCode, valueKey)} 不重复——
     * 空集合会让 XML 的 {@code <foreach>} 生成 {@code insert ... values} 后没有内容的非法 SQL，
     * 重复值会撞 {@code uk_cms_content_index}。调用方（{@code ContentService.syncRelations}）
     * 已按这两条过滤。
     */
    int insertBatch(@Param("rows") List<CmsContentIndex> rows);
}
