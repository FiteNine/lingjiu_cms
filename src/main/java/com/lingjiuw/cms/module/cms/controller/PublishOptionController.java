package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.PublishOptionSaveRequest;
import com.lingjiuw.cms.module.cms.dto.PublishOptionVO;
import com.lingjiuw.cms.module.cms.service.PublishOptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 站点发布选项（static-publish.md §2.7）：读当前站点的全部选项、批量保存、删除一条 */
@RestController
@RequestMapping("/api/cms/publish/options")
@RequiredArgsConstructor
public class PublishOptionController {

    private final PublishOptionService publishOptionService;

    /** 当前站点的全部发布选项：{optionCode, value, valueType} */
    @GetMapping
    @PreAuthorize("hasAuthority('cms:publish:option:list')")
    public Result<List<PublishOptionVO>> list() {
        return Result.ok(publishOptionService.list());
    }

    /** 批量保存 {options:[{optionCode,value}]}：未知 optionCode 按新增处理 */
    @PutMapping
    @PreAuthorize("hasAuthority('cms:publish:option:edit')")
    @OperLog(module = "发布选项", action = "保存发布选项")
    public Result<Void> save(@RequestBody @Valid PublishOptionSaveRequest request) {
        publishOptionService.save(request);
        return Result.ok();
    }

    /** 删除一条选项：{@code optionCode} 如 page.archive。删掉 = 回到引擎默认值 */
    @DeleteMapping
    @PreAuthorize("hasAuthority('cms:publish:option:delete')")
    @OperLog(module = "发布选项", action = "删除发布选项")
    public Result<Void> delete(@RequestParam String optionCode) {
        publishOptionService.delete(optionCode);
        return Result.ok();
    }
}
