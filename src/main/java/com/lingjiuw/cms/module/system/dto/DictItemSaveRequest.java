package com.lingjiuw.cms.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 新增/编辑字典项请求 */
public record DictItemSaveRequest(
        @NotNull(message = "字典类型不能为空") Long typeId,
        @NotBlank(message = "字典标签不能为空")
        @Size(max = 64, message = "字典标签长度不能超过64") String label,
        @NotBlank(message = "字典值不能为空")
        @Size(max = 64, message = "字典值长度不能超过64") String value,
        Integer sort,
        Integer status,
        @Size(max = 255, message = "备注长度不能超过255") String remark) {
}
