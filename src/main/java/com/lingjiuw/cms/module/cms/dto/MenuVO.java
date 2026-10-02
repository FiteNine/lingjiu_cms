package com.lingjiuw.cms.module.cms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 导航菜单（static-publish.md §2.4），带菜单项树 */
@Data
public class MenuVO {

    private Long id;
    private String code;
    private String name;
    private Integer status;
    private Integer sort;
    private List<MenuItemVO> items = new ArrayList<>();
}
