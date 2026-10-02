package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.FieldSaveRequest;
import com.lingjiuw.cms.module.cms.dto.FieldVO;
import com.lingjiuw.cms.module.cms.entity.CmsContentIndex;
import com.lingjiuw.cms.module.cms.entity.CmsContentType;
import com.lingjiuw.cms.module.cms.entity.CmsField;
import com.lingjiuw.cms.module.cms.mapper.CmsContentIndexMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTypeMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsFieldMapper;
import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 字段定义（cms_field）：某个内容类型下的自定义字段，见 static-publish.md §2.2。
 *
 * <p>这一层收口三件事：字段名不得是保留名（§5.1 的 6 个，模板里的 {@code [field:xxx/]} 会与
 * 具名作用域撞车）、字段类型必须是 {@link FieldType} 的 19 个之一、开关列统一成 0/1。
 * {@code (site_id, type_code, code)} 站点内唯一由建表的部分唯一索引兜底，这里先查一次是为了给出中文提示。
 */
@Service
@RequiredArgsConstructor
public class FieldService {

    private final CmsFieldMapper fieldMapper;
    private final CmsContentTypeMapper contentTypeMapper;
    private final CmsContentIndexMapper contentIndexMapper;

    /** 某类型下的字段：sort 升序，再按 id 兜底（sort 相同的两个字段顺序必须稳定） */
    public List<FieldVO> list(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            throw new BizException("请先选择内容类型");
        }
        return fieldMapper.selectList(Wrappers.<CmsField>lambdaQuery()
                        .eq(CmsField::getSiteId, SiteContext.siteId())
                        .eq(CmsField::getTypeCode, typeCode.trim())
                        .orderByAsc(CmsField::getSort)
                        .orderByAsc(CmsField::getId)).stream()
                .map(FieldService::toVO)
                .toList();
    }

    public void create(FieldSaveRequest request) {
        String typeCode = request.typeCode().trim();
        requireType(typeCode);
        String code = request.code().trim();
        checkCode(code);
        checkCodeUnique(typeCode, code);
        CmsField field = new CmsField();
        applyRequest(field, request);
        checkEnumOptions(field.getFieldType(), field.getOptions());
        field.setSiteId(SiteContext.siteId());
        try {
            fieldMapper.insert(field);
        } catch (DuplicateKeyException e) {
            // (site_id, type_code, code) 的部分唯一索引是最终保证：并发下 checkCodeUnique 可能同时通过
            throw new BizException("该类型下已存在同名字段：" + code);
        }
    }

    public void update(Long id, FieldSaveRequest request) {
        CmsField field = requireField(id);
        // 字段名与所属类型是"模板 + 索引表 + cms_content.data 的键"三处共用的句柄：改名要同时改
        // cms_content_index.field_code 与每条内容 jsonb 里的键，是数据迁移而不是编辑，因此不允许改
        if (!field.getCode().equals(request.code().trim())
                || !field.getTypeCode().equals(request.typeCode().trim())) {
            throw new BizException("字段名与所属类型创建后不能修改：" + field.getCode());
        }
        applyRequest(field, request);
        checkEnumOptions(field.getFieldType(), field.getOptions());
        fieldMapper.updateById(field);
    }

    /**
     * 删除字段：连同它的字段索引行一起清掉。索引表是 {@code where} / {@code orderby} / facet 的
     * 唯一数据源（§2.5），字段定义没了却留着索引行，就是留给引擎一堆永远对不上的取值。
     * 索引行按逻辑删除处理（与表上的部分唯一索引 {@code where deleted = 0} 一致）。
     */
    @Transactional
    public void delete(Long id) {
        CmsField field = requireField(id);
        fieldMapper.deleteById(id);
        contentIndexMapper.delete(Wrappers.<CmsContentIndex>lambdaQuery()
                .eq(CmsContentIndex::getSiteId, field.getSiteId())
                .eq(CmsContentIndex::getTypeCode, field.getTypeCode())
                .eq(CmsContentIndex::getFieldCode, field.getCode()));
    }

    /* ---------------- 取值校验 ---------------- */

    /**
     * 请求 → 实体。可空入参按"没提交就别动"处理：{@code applyRequest} 由 create 与 update 共用，
     * 编辑时漏传某个开关（如只改 label）不能把库里已有的 indexed / searchable 静默重置为 0。
     */
    private void applyRequest(CmsField field, FieldSaveRequest request) {
        field.setTypeCode(request.typeCode().trim());
        field.setCode(request.code().trim());
        field.setLabel(request.label().trim());
        field.setFieldType(fieldType(request.fieldType()));
        field.setFormatter(blankToNull(request.formatter()));
        field.setRaw(request.raw() == null ? 0 : request.raw());
        field.setRequired(request.required() == null ? 0 : request.required());
        field.setDefaultValue(blankToNull(request.defaultValue()));
        field.setOptions(blankToNull(request.options()));
        field.setSearchable(request.searchable() == null ? keep(field.getSearchable()) : request.searchable());
        field.setIndexed(request.indexed() == null ? keep(field.getIndexed()) : request.indexed());
        field.setCrossSite(request.crossSite() == null ? 0 : request.crossSite());
        field.setHelp(blankToNull(request.help()));
        field.setSort(request.sort() == null ? 0 : request.sort());
    }

    /** 开关类字段的默认值：新建（库里还没有值）写 0，编辑时保留库里已有的值 */
    private static Integer keep(Integer current) {
        return current == null ? 0 : current;
    }

    /**
     * ENUM / ENUM_MULTI 的 options 是"值[:标签]"逗号分隔的语法，ContentService.enumValues 只认半角
     * 逗号与半角冒号：多余的逗号、全角冒号与重复值存进去后会让取值静默变形，所以写库前按同一口径拦下。
     */
    private static void checkEnumOptions(String fieldType, String options) {
        if (!isEnumType(fieldType)) {
            return;
        }
        if (!StringUtils.hasText(options)) {
            throw new BizException("枚举类型必须配置选项（值[:标签]，逗号分隔）");
        }
        String[] pieces = options.split(",", -1);
        List<String> items = new ArrayList<>(pieces.length);
        for (String piece : pieces) {
            String item = piece.trim();
            if (item.isEmpty()) {
                throw new BizException("选项存在空项，请检查多余的逗号");
            }
            items.add(item);
        }
        for (String item : items) {
            if (item.indexOf('：') >= 0) {
                throw new BizException("选项请使用半角冒号");
            }
        }
        List<String> values = new ArrayList<>(items.size());
        for (String item : items) {
            int colon = item.indexOf(':');
            String value = colon < 0 ? item : item.substring(0, colon).trim();
            if (value.isEmpty()) {
                throw new BizException("存在没有值的选项");
            }
            values.add(value);
        }
        if (ContentService.enumValues(options).size() < values.size()) {
            Set<String> seen = new HashSet<>();
            for (String value : values) {
                if (!seen.add(value)) {
                    throw new BizException("选项值重复：" + value);
                }
            }
        }
    }

    /** 与 ContentService.fieldType 同口径：按枚举名忽略大小写与空白匹配，表外取值不算枚举类型。 */
    private static boolean isEnumType(String fieldType) {
        if (fieldType == null || fieldType.isBlank()) {
            return false;
        }
        String text = fieldType.trim();
        for (FieldType type : FieldType.values()) {
            if (type.name().equalsIgnoreCase(text)) {
                return type == FieldType.ENUM || type == FieldType.ENUM_MULTI;
            }
        }
        return false;
    }

    /** 字段类型必须是 §2.2 的 19 个之一；库里统一存大写，引擎匹配时不区分大小写 */
    private static String fieldType(String value) {
        String text = value.trim().toUpperCase(Locale.ROOT);
        for (FieldType type : FieldType.values()) {
            if (type.name().equals(text)) {
                return text;
            }
        }
        throw new BizException("字段类型不支持：" + value + "（可选："
                + String.join(" ", Arrays.stream(FieldType.values()).map(Enum::name).toList()) + "）");
    }

    /** 字段名的基础约束：保留名不得使用（与编译期 E2005 同一份清单，见 §5.1） */
    private static void checkCode(String code) {
        if (BuiltinFields.RESERVED_NAMES.contains(code)) {
            throw new BizException("字段名不能是保留名（"
                    + String.join(" ", BuiltinFields.RESERVED_NAMES) + "）：" + code);
        }
    }

    private void checkCodeUnique(String typeCode, String code) {
        boolean exists = fieldMapper.exists(Wrappers.<CmsField>lambdaQuery()
                .eq(CmsField::getSiteId, SiteContext.siteId())
                .eq(CmsField::getTypeCode, typeCode)
                .eq(CmsField::getCode, code));
        if (exists) {
            throw new BizException("该类型下已存在同名字段：" + code);
        }
    }

    /** 字段必须挂在当前站点的类型上：类型不存在时字段永远不会被任何模板读到 */
    private void requireType(String typeCode) {
        boolean exists = contentTypeMapper.exists(Wrappers.<CmsContentType>lambdaQuery()
                .eq(CmsContentType::getSiteId, SiteContext.siteId())
                .eq(CmsContentType::getCode, typeCode));
        if (!exists) {
            throw new BizException("内容类型不存在：" + typeCode);
        }
    }

    /** 当前站点的字段：别的站点的 id 一律当作不存在 */
    private CmsField requireField(Long id) {
        CmsField field = fieldMapper.selectOne(Wrappers.<CmsField>lambdaQuery()
                .eq(CmsField::getId, id)
                .eq(CmsField::getSiteId, SiteContext.siteId()));
        if (field == null) {
            throw new BizException("字段不存在或已被删除");
        }
        return field;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static FieldVO toVO(CmsField field) {
        return new FieldVO(field.getId(), field.getTypeCode(), field.getCode(), field.getLabel(),
                field.getFieldType(), field.getFormatter(), field.getRaw(), field.getRequired(),
                field.getDefaultValue(), field.getOptions(), field.getSearchable(), field.getIndexed(),
                field.getCrossSite(), field.getHelp(), field.getSort(),
                field.getCreateTime(), field.getUpdateTime());
    }
}
