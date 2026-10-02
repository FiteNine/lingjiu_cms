package com.lingjiuw.cms.module.system.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 菜单树节点 */
@Data
public class MenuNode {

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String icon;
    private String perms;
    private String type;
    private Integer sort;
    private Integer visible;
    private Integer status;
    private LocalDateTime createTime;

    /** 自引用字段不参与 equals/hashCode/toString，避免沿整棵树递归 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<MenuNode> children = new ArrayList<>();
}
