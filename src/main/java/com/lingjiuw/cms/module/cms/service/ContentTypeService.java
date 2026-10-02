package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.ContentTypeSaveRequest;
import com.lingjiuw.cms.module.cms.dto.ContentTypeVO;
import com.lingjiuw.cms.module.cms.entity.CmsContent;
import com.lingjiuw.cms.module.cms.entity.CmsContentType;
import com.lingjiuw.cms.module.cms.entity.CmsField;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTypeMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsFieldMapper;
import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 内容类型（cms_content_type）：站点自助定义内容模型的入口，见 static-publish.md §2.1。
 *
 * <p>这一层负责把"能进库的取值"收口：形态三选一、标识站点内唯一且不得是保留名、{@code options}
 * 必须是 JSON 对象。模板编译期的 E2005（保留名）与 E2006（类型不存在）都是这些取值的下游，
 * 在这里拦住比发布失败后再回头找便宜得多。
 */
@Service
@RequiredArgsConstructor
public class ContentTypeService {

    /** 类型形态（§2.1）：CONTENT = 列表 + 详情，SINGLE = 全站仅一份，TREE = 父子层级 */
    private static final Set<String> VALID_KINDS = Set.of("CONTENT", "SINGLE", "TREE");

    /** 每页条数兜底值，与建表时的 default 20 一致 */
    private static final int DEFAULT_PER_PAGE = 20;

    private final CmsContentTypeMapper contentTypeMapper;
    private final CmsFieldMapper fieldMapper;
    private final CmsContentMapper contentMapper;
    private final ObjectMapper objectMapper;

    /** 只列当前站点的类型：类型由各站点自己定义（site_id 是唯一键的第一段） */
    public PageResult<ContentTypeVO> page(long page, long size, String keyword) {
        IPage<CmsContentType> result = contentTypeMapper.selectPage(new Page<>(page, size),
                Wrappers.<CmsContentType>lambdaQuery()
                        .eq(CmsContentType::getSiteId, SiteContext.siteId())
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(CmsContentType::getCode, keyword)
                                .or()
                                .like(CmsContentType::getName, keyword))
                        .orderByAsc(CmsContentType::getSort)
                        .orderByAsc(CmsContentType::getId));
        return PageResult.of(result.convert(this::toVO));
    }

    /** 下拉选项：字段页 / 内容页选类型用，登录即可读，只给 code 与显示名 */
    public List<ContentTypeVO.Option> listOptions() {
        return contentTypeMapper.selectList(Wrappers.<CmsContentType>lambdaQuery()
                        .eq(CmsContentType::getSiteId, SiteContext.siteId())
                        .orderByAsc(CmsContentType::getSort)
                        .orderByAsc(CmsContentType::getId)).stream()
                .map(type -> new ContentTypeVO.Option(type.getCode(), type.getName()))
                .toList();
    }

    public ContentTypeVO detail(Long id) {
        return toVO(requireType(id));
    }

    public void create(ContentTypeSaveRequest request) {
        String code = request.code().trim();
        checkCode(code);
        checkCodeUnique(code);
        CmsContentType type = new CmsContentType();
        applyRequest(type, request);
        type.setSiteId(SiteContext.siteId());
        try {
            contentTypeMapper.insertType(type);
        } catch (DuplicateKeyException e) {
            // (site_id, code) 的部分唯一索引是最终保证：并发下 checkCodeUnique 可能同时通过
            throw new BizException("类型标识已存在：" + code);
        }
    }

    public void update(Long id, ContentTypeSaveRequest request) {
        CmsContentType type = requireType(id);
        // 标识是不可变句柄：cms_field.type_code 与 cms_content.type_code 都按它关联，改一个字母
        // 就等于把字段定义与内容一起扔掉（三张表要一起改，还得改名 cms_content.data 里的键）
        if (!type.getCode().equals(request.code().trim())) {
            throw new BizException("类型标识创建后不能修改：" + type.getCode());
        }
        applyRequest(type, request);
        contentTypeMapper.updateType(type);
    }

    /**
     * 删除类型。同一事务里先确认该类型下没有未删的内容与字段——两者都按 type_code 关联，
     * 类型删掉后它们就成了孤儿（引擎读不到类型定义，模板直接编译失败），所以这里是拒绝而不是级联。
     */
    @Transactional
    public void delete(Long id) {
        CmsContentType type = requireType(id);
        if (contentMapper.exists(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getSiteId, type.getSiteId())
                .eq(CmsContent::getTypeCode, type.getCode()))) {
            throw new BizException("该类型下还有内容，请先清空内容后再删除");
        }
        if (fieldMapper.exists(Wrappers.<CmsField>lambdaQuery()
                .eq(CmsField::getSiteId, type.getSiteId())
                .eq(CmsField::getTypeCode, type.getCode()))) {
            throw new BizException("该类型下还有字段定义，请先清空字段后再删除");
        }
        contentTypeMapper.deleteById(id);
    }

    /* ---------------- 取值校验 ---------------- */

    private void applyRequest(CmsContentType type, ContentTypeSaveRequest request) {
        String kind = request.kind().trim().toUpperCase(Locale.ROOT);
        if (!VALID_KINDS.contains(kind)) {
            throw new BizException("类型形态只能是 CONTENT / SINGLE / TREE：" + request.kind());
        }
        type.setCode(request.code().trim());
        type.setName(request.name().trim());
        type.setKind(kind);
        // TREE 隐含层级（表注释 §2.1）：形态选了层级内容，就不必再单独勾一次
        type.setHierarchical("TREE".equals(kind) ? 1
                : request.hierarchical() == null ? 0 : request.hierarchical());
        type.setDetailUrlPattern(blankToNull(request.detailUrlPattern()));
        type.setListUrlPattern(blankToNull(request.listUrlPattern()));
        type.setDetailTemplate(blankToNull(request.detailTemplate()));
        type.setListTemplate(blankToNull(request.listTemplate()));
        type.setPaginateBody(blankToNull(request.paginateBody()));
        type.setSortField(blankToNull(request.sortField()));
        type.setSortOrder(sortOrder(request.sortOrder()));
        type.setPerPage(request.perPage() == null ? DEFAULT_PER_PAGE : request.perPage());
        type.setSeoTitleField(blankToNull(request.seoTitleField()));
        type.setSeoDescField(blankToNull(request.seoDescField()));
        type.setOptions(options(request.options()));
        type.setStatus(request.status() == null ? 1 : request.status());
        type.setSort(request.sort() == null ? 0 : request.sort());
    }

    /** 排序方向：库里一律存小写（与内置类型播种的 asc / desc 一致），空 = 引擎默认 publishTime desc */
    private static String sortOrder(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String order = value.trim().toLowerCase(Locale.ROOT);
        if (!"asc".equals(order) && !"desc".equals(order)) {
            throw new BizException("排序方向只能是 asc 或 desc：" + value);
        }
        return order;
    }

    /**
     * options 是 jsonb 原文：空 = 清空。非空时必须是 JSON **对象**——引擎按对象读
     * {@code facets} 与 {@code searchable}（§7.2、§6.1.1），数组或裸值读不出来。
     */
    private String options(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        JsonNode node;
        try {
            node = objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw new BizException("类型选项不是合法的 JSON：" + e.getOriginalMessage());
        }
        if (node == null || !node.isObject()) {
            throw new BizException("类型选项必须是 JSON 对象，例如 {\"facets\":[],\"searchable\":true}");
        }
        // 用解析后的节点重新序列化：readTree 默认不拒尾随内容（{"a":1}garbage 也算合法对象），
        // 原样返回会让 jsonb 转换在写库时抛底层 DB 异常
        return node.toString();
    }

    /** 类型标识的基础约束：保留名不得使用（与编译期 E2005 同一份清单，见 §5.1） */
    private static void checkCode(String code) {
        if (BuiltinFields.RESERVED_NAMES.contains(code)) {
            throw new BizException("类型标识不能是保留名（"
                    + String.join(" ", BuiltinFields.RESERVED_NAMES) + "）：" + code);
        }
    }

    private void checkCodeUnique(String code) {
        boolean exists = contentTypeMapper.exists(Wrappers.<CmsContentType>lambdaQuery()
                .eq(CmsContentType::getSiteId, SiteContext.siteId())
                .eq(CmsContentType::getCode, code));
        if (exists) {
            throw new BizException("类型标识已存在：" + code);
        }
    }

    /** 当前站点的类型：别的站点的 id 一律当作不存在 */
    private CmsContentType requireType(Long id) {
        CmsContentType type = contentTypeMapper.selectOne(Wrappers.<CmsContentType>lambdaQuery()
                .eq(CmsContentType::getId, id)
                .eq(CmsContentType::getSiteId, SiteContext.siteId()));
        if (type == null) {
            throw new BizException("内容类型不存在或已被删除");
        }
        return type;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ContentTypeVO toVO(CmsContentType type) {
        return new ContentTypeVO(type.getId(), type.getCode(), type.getName(), type.getKind(),
                type.getHierarchical(), type.getDetailUrlPattern(), type.getListUrlPattern(),
                type.getDetailTemplate(), type.getListTemplate(), type.getPaginateBody(),
                type.getSortField(), type.getSortOrder(), type.getPerPage(), type.getSeoTitleField(),
                type.getSeoDescField(), type.getOptions(), type.getStatus(), type.getSort(),
                type.getCreateTime(), type.getUpdateTime());
    }
}
