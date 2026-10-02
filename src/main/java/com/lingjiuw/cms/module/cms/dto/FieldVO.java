package com.lingjiuw.cms.module.cms.dto;

import java.time.LocalDateTime;

/**
 * 字段定义视图对象（cms_field）。字段与表列一一对应，前端编辑表单原样回填后即可提交，
 * 所以开关列保持 0/1，不做 Boolean 转换。
 */
public record FieldVO(
        Long id,
        String typeCode,
        String code,
        String label,
        String fieldType,
        String formatter,
        Integer raw,
        Integer required,
        String defaultValue,
        String options,
        Integer searchable,
        Integer indexed,
        Integer crossSite,
        String help,
        Integer sort,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
