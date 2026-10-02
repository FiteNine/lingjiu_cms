package com.lingjiuw.cms.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.system.dto.DictItemSaveRequest;
import com.lingjiuw.cms.module.system.dto.DictTypeSaveRequest;
import com.lingjiuw.cms.module.system.entity.SysDictItem;
import com.lingjiuw.cms.module.system.entity.SysDictType;
import com.lingjiuw.cms.module.system.mapper.SysDictItemMapper;
import com.lingjiuw.cms.module.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictItemMapper dictItemMapper;

    public PageResult<SysDictType> typePage(long page, long size, String code, String name) {
        return PageResult.of(dictTypeMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysDictType>lambdaQuery()
                        .like(StringUtils.hasText(code), SysDictType::getCode, code)
                        .like(StringUtils.hasText(name), SysDictType::getName, name)
                        .orderByAsc(SysDictType::getId)));
    }

    public void createType(DictTypeSaveRequest request) {
        checkTypeCodeUnique(request.code(), null);
        SysDictType type = new SysDictType();
        applyType(type, request);
        try {
            dictTypeMapper.insert(type);
        } catch (DuplicateKeyException e) {
            // 唯一索引是最终保证：并发下 checkTypeCodeUnique 可能同时通过，这里兜成业务提示
            throw new BizException("字典编码已存在");
        }
    }

    public void updateType(Long id, DictTypeSaveRequest request) {
        SysDictType type = requireType(id);
        checkTypeCodeUnique(request.code(), id);
        applyType(type, request);
        try {
            dictTypeMapper.updateById(type);
        } catch (DuplicateKeyException e) {
            // 改字典编码同样可能和别人撞车，兜成业务提示而不是 500
            throw new BizException("字典编码已存在");
        }
    }

    @Transactional
    public void deleteType(Long id) {
        requireType(id);
        dictTypeMapper.deleteById(id);
        dictItemMapper.delete(Wrappers.<SysDictItem>lambdaQuery().eq(SysDictItem::getTypeId, id));
    }

    /** 按类型编码或类型 id 查询字典项 */
    public List<SysDictItem> items(Long typeId, String code) {
        Long targetTypeId = typeId;
        if (targetTypeId == null && StringUtils.hasText(code)) {
            SysDictType type = dictTypeMapper.selectOne(Wrappers.<SysDictType>lambdaQuery()
                    .eq(SysDictType::getCode, code));
            if (type == null) {
                return List.of();
            }
            targetTypeId = type.getId();
        }
        if (targetTypeId == null) {
            throw new BizException("请指定字典类型");
        }
        return dictItemMapper.selectList(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getTypeId, targetTypeId)
                .orderByAsc(SysDictItem::getSort)
                .orderByAsc(SysDictItem::getId));
    }

    public void createItem(DictItemSaveRequest request) {
        requireType(request.typeId());
        SysDictItem item = new SysDictItem();
        applyItem(item, request);
        try {
            dictItemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // (type_id, value) 部分唯一索引是最终保证：并发下同名值会同时通过应用层检查
            throw new BizException("该字典类型下已存在同名值");
        }
    }

    public void updateItem(Long id, DictItemSaveRequest request) {
        SysDictItem item = dictItemMapper.selectById(id);
        if (item == null) {
            throw new BizException("字典项不存在或已被删除");
        }
        requireType(request.typeId());
        applyItem(item, request);
        try {
            dictItemMapper.updateById(item);
        } catch (DuplicateKeyException e) {
            // 改 value 同样可能和同类型的其它项撞车，兜成业务提示而不是 500
            throw new BizException("该字典类型下已存在同名值");
        }
    }

    public void deleteItem(Long id) {
        if (dictItemMapper.deleteById(id) == 0) {
            throw new BizException("字典项不存在或已被删除");
        }
    }

    private void applyType(SysDictType type, DictTypeSaveRequest request) {
        type.setCode(request.code());
        type.setName(request.name());
        type.setRemark(request.remark());
    }

    private void applyItem(SysDictItem item, DictItemSaveRequest request) {
        item.setTypeId(request.typeId());
        item.setLabel(request.label());
        item.setValue(request.value());
        item.setSort(request.sort() == null ? 0 : request.sort());
        item.setStatus(request.status() == null ? 1 : request.status());
        item.setRemark(request.remark());
    }

    private void checkTypeCodeUnique(String code, Long excludeId) {
        Long count = dictTypeMapper.selectCount(Wrappers.<SysDictType>lambdaQuery()
                .eq(SysDictType::getCode, code)
                .ne(excludeId != null, SysDictType::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("字典编码已存在");
        }
    }

    private SysDictType requireType(Long id) {
        SysDictType type = dictTypeMapper.selectById(id);
        if (type == null) {
            throw new BizException("字典类型不存在或已被删除");
        }
        return type;
    }
}
