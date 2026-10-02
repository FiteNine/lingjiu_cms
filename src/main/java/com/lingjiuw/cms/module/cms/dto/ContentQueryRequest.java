package com.lingjiuw.cms.module.cms.dto;

/**
 * 内容分页查询条件（接口上的同名查询参数；控制器给默认值，这里只做归一）。
 *
 * <p>{@code size} 有上限：这个是后台列表接口，前端最大的 pageSize 是 100，
 * 不设上限时 {@code ?size=1000000} 会一次性拉走全站内容。
 */
public record ContentQueryRequest(
        long page,
        long size,
        String typeCode,
        String status,
        String keyword) {

    /** 与前端列表的 page-sizes 上限一致 */
    private static final long MAX_SIZE = 100;

    public ContentQueryRequest {
        page = page < 1 ? 1 : page;
        size = size < 1 ? 20 : Math.min(size, MAX_SIZE);
    }
}
