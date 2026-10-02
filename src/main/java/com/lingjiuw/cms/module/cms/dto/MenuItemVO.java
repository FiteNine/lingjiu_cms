package com.lingjiuw.cms.module.cms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单项树节点，字段照 {@code cms_menu_item} 的列（static-publish.md §2.4）。
 *
 * <p>返回的是**原始取值**（不是引擎渲染时算出来的 URL 与名称）：后台编辑页要能把它们填回表单。
 */
@Data
public class MenuItemVO {

    private Long id;
    private Long parentId;
    private String label;
    private String kind;
    private Long refId;
    private String refCode;
    private String url;
    private String target;
    private String rel;
    private Integer visible;
    private Integer sort;
    private List<MenuItemVO> children = new ArrayList<>();
}
