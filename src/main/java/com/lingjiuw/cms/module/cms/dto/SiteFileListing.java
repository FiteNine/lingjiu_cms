package com.lingjiuw.cms.module.cms.dto;

import java.util.List;

/**
 * 站点目录下一个路径的子目录与文件。
 *
 * <p>{@code rootPath} 是站点目录在服务器上的绝对路径（只用于页面提示），{@code path} 是相对站点目录的
 * 当前路径，站点目录本身为空串。两者的子项 path 都可以直接回传给接口。
 */
public record SiteFileListing(String rootPath, String path, List<DirNode> dirs, List<FileNode> files) {

    /** 子目录；path 相对站点目录 */
    public record DirNode(String name, String path) {
    }

    /** 文件；kind 决定能不能在线处理：TEXT 可编辑、IMAGE 可预览、OTHER 只看文件信息 */
    public record FileNode(String name, String path, long size, String kind) {
    }
}
