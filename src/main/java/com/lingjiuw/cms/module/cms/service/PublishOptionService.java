package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.PublishOptionSaveRequest;
import com.lingjiuw.cms.module.cms.dto.PublishOptionVO;
import com.lingjiuw.cms.module.cms.entity.CmsSitePublishOption;
import com.lingjiuw.cms.module.cms.mapper.CmsSitePublishOptionMapper;
import com.lingjiuw.cms.module.cms.publish.provider.DbContentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点发布选项（static-publish.md §2.7）。
 *
 * <p>{@code cms_site_publish_option} 只有 {@code (site_id, option_code, value)} 三列，**没有类型列**：
 * 选项值类型硬编码在发布引擎的三份清单里（{@link DbContentProvider} 的 public 常量）。本类只做四件事——
 * 把它读成 {@code {optionCode, value, valueType}}、按类型校验后写回、未知 optionCode 按新增处理、
 * 按 optionCode 逻辑删掉一条（删掉 = 回到引擎默认）。
 *
 * <p>取值口径与引擎一致（§2.7）：空串 = "未设置，用引擎默认"，所以空值一律落成空串，
 * 发布时 {@code DbContentProvider.parseOption} 会当成"没有配"。
 */
@Service
@RequiredArgsConstructor
public class PublishOptionService {

    /** 布尔选项：只接受 1 / 0（也接受 true / false） */
    private static final String TYPE_BOOL = "bool";

    /** 计数选项：只接受整数 */
    private static final String TYPE_NUMBER = "number";

    /** JSON 字面量选项：必须是合法 JSON */
    private static final String TYPE_JSON = "json";

    /** 其余选项：原样字符串（含逗号串形态） */
    private static final String TYPE_TEXT = "text";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CmsSitePublishOptionMapper optionMapper;

    /** 当前站点的全部发布选项，按 option_code 升序（与引擎读的顺序一致） */
    public List<PublishOptionVO> list() {
        return optionMapper.selectList(Wrappers.<CmsSitePublishOption>lambdaQuery()
                        .eq(CmsSitePublishOption::getSiteId, SiteContext.siteId())
                        .orderByAsc(CmsSitePublishOption::getOptionCode)).stream()
                .map(row -> new PublishOptionVO(row.getOptionCode(), row.getValue(), valueType(row.getOptionCode())))
                .toList();
    }

    /** 批量保存：同一个事务；已存在的更新、没有的新增（键由后台维护，这里不卡封闭清单） */
    @Transactional
    public void save(PublishOptionSaveRequest request) {
        Long siteId = SiteContext.siteId();
        // 已存在的行一次查完：整表保存时逐条 selectOne 就是 N 次查询（写入仍是逐条，Mapper 没有批量写）
        List<String> codes = request.options().stream()
                .map(item -> item.optionCode().trim()).distinct().toList();
        Map<String, CmsSitePublishOption> existing = new HashMap<>();
        if (!codes.isEmpty()) {
            for (CmsSitePublishOption row : optionMapper.selectList(Wrappers.<CmsSitePublishOption>lambdaQuery()
                    .eq(CmsSitePublishOption::getSiteId, siteId)
                    .in(CmsSitePublishOption::getOptionCode, codes))) {
                existing.put(row.getOptionCode(), row);
            }
        }
        for (PublishOptionSaveRequest.Item item : request.options()) {
            String code = item.optionCode().trim();
            String value = checkValue(code, item.value());
            CmsSitePublishOption exists = existing.get(code);
            if (exists == null) {
                CmsSitePublishOption row = new CmsSitePublishOption();
                row.setSiteId(siteId);
                row.setOptionCode(code);
                row.setValue(value);
                try {
                    optionMapper.insert(row);
                } catch (DuplicateKeyException e) {
                    // (site_id, option_code) 的唯一索引是最终保证：并发保存时上面的判存可能同时通过
                    throw new BizException("发布选项保存冲突，请重试：" + code);
                }
                // 同一批里重复出现的 code 也要走更新，不能重复插入
                existing.put(code, row);
            } else {
                exists.setValue(value);
                optionMapper.updateById(exists);
            }
        }
    }

    /**
     * 删除一条选项（逻辑删，@TableLogic）。删掉的效果与"值存空串"一样，都是"未设置，用引擎默认"，
     * 区别只在后台表单里不再显示这一行；删掉之后同一个 optionCode 还能重新新增
     * （部分唯一索引是 {@code (site_id, option_code) where deleted = 0}，见 §2.7）。
     */
    @Transactional
    public void delete(String optionCode) {
        String code = optionCode == null ? "" : optionCode.trim();
        if (code.isEmpty()) {
            throw new BizException("请选择要删除的发布选项");
        }
        // 逻辑删除的影响行数就是存在性判定：不用先查一次，也就没有查询与删除之间的竞态
        if (optionMapper.delete(Wrappers.<CmsSitePublishOption>lambdaQuery()
                .eq(CmsSitePublishOption::getSiteId, SiteContext.siteId())
                .eq(CmsSitePublishOption::getOptionCode, code)) == 0) {
            throw new BizException("发布选项不存在或已被删除：" + code);
        }
    }

    /** 取值类型：三份清单（发布引擎）决定 bool / number / json，其余一律 text */
    private static String valueType(String optionCode) {
        if (DbContentProvider.BOOLEAN_OPTIONS.contains(optionCode)) {
            return TYPE_BOOL;
        }
        if (DbContentProvider.NUMBER_OPTIONS.contains(optionCode)) {
            return TYPE_NUMBER;
        }
        if (DbContentProvider.JSON_OPTIONS.contains(optionCode)) {
            return TYPE_JSON;
        }
        return TYPE_TEXT;
    }

    /**
     * 按类型校验并归一取值：空值一律存空串（§2.7）；bool / number / json 存去掉首尾空白后的值，
     * text 原样保存（逗号串、前导空白都是它的合法形态，不能被改）。
     */
    private static String checkValue(String optionCode, String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        switch (valueType(optionCode)) {
            case TYPE_BOOL -> {
                if (!"1".equals(value) && !"0".equals(value)
                        && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new BizException("发布选项 " + optionCode + " 的值只能是 1、0、true 或 false");
                }
            }
            case TYPE_NUMBER -> {
                // 引擎按 Integer 解析这些选项：超出 32 位整数会解析失败并静默回退默认值，
                // 与其"保存成功但不生效"，不如在这里按同一口径拒绝
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new BizException("发布选项 " + optionCode + " 的值必须是 32 位整数：" + value);
                }
            }
            case TYPE_JSON -> {
                try {
                    MAPPER.readTree(value);
                } catch (Exception e) {
                    throw new BizException("发布选项 " + optionCode + " 的值必须是合法的 JSON：" + e.getMessage());
                }
            }
            default -> {
                return raw;   // text：原样存
            }
        }
        return value;
    }
}
