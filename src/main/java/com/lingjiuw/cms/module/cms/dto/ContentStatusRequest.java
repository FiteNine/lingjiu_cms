package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.constraints.NotBlank;

/** 修改内容状态请求 */
public record ContentStatusRequest(@NotBlank(message = "状态不能为空") String status) {
}
