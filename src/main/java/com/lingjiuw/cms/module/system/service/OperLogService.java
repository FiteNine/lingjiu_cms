package com.lingjiuw.cms.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.module.system.dto.OperLogVO;
import com.lingjiuw.cms.module.system.entity.SysOperLog;
import com.lingjiuw.cms.module.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OperLogService {

    private final SysOperLogMapper operLogMapper;

    public PageResult<OperLogVO> page(long page, long size, String username, String action) {
        IPage<SysOperLog> result = operLogMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysOperLog>lambdaQuery()
                        .like(StringUtils.hasText(username), SysOperLog::getUsername, username)
                        .like(StringUtils.hasText(action), SysOperLog::getAction, action)
                        .orderByDesc(SysOperLog::getId));
        return new PageResult<>(result.getRecords().stream().map(OperLogVO::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }
}
