package com.lingjiuw.cms.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增/编辑字典类型请求 */
public record DictTypeSaveRequest(
        @NotBlank(message = "字典编码不能为空")
        @Size(max = 64, message = "字典编码长度不能超过64") String code,
        @NotBlank(message = "字典名称不能为空")
        @Size(max = 64, message = "字典名称长度不能超过64") String name,
        @Size(max = 255, message = "备注长度不能超过255") String remark) {
}
