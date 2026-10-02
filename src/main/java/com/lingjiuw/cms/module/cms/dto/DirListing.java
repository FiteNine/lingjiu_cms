package com.lingjiuw.cms.module.cms.dto;

import java.util.List;

/** 站点根目录某一路径下的子目录；path 为相对 cms.site.root-dir 的路径，根目录为空串 */
public record DirListing(String path, List<DirNode> dirs) {

    /** 子目录；path 为相对 cms.site.root-dir 的路径，可直接回填表单 */
    public record DirNode(String name, String path) {
    }
}
