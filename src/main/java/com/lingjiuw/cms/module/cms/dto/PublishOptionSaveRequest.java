package com.lingjiuw.cms.module.cms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量保存站点发布选项请求：{@code {options:[{optionCode,value}]}}。
 *
 * <p>取值本身按选项类型校验（bool / number / json），在 Service 里做；未知的 optionCode 按新增处理。
 *
 * <p>{@code optionCode} 的形态固定是「命名空间.驼峰」（{@code page.archive} / {@code publish.cron}），
 * 与 {@code DbContentProvider} 的三份清单同一个写法；数量上限则是因为 Service 对每一条都要查一次库。
 */
public record PublishOptionSaveRequest(
        @NotEmpty(message = "发布选项不能为空")
        @Size(max = 200, message = "单次最多保存 200 个发布选项")
        @Valid List<Item> options) {

    /** 一个待保存的选项；value 为 null 或空白时存空串（空串 = "未设置，用引擎默认"，§2.7） */
    public record Item(
            @NotBlank(message = "选项键不能为空")
            @Size(max = 64, message = "选项键最长 64 字符")
            @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*)*$",
                    message = "选项键要写成「命名空间.驼峰」，如 page.archive")
            String optionCode,
            String value) {
    }
}
