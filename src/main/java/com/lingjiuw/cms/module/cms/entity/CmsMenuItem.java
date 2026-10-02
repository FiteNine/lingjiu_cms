package com.lingjiuw.cms.module.cms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 导航菜单项，见 static-publish.md §2.4。
 * "这一项是不是当前页"由引擎比对得出，模板里不写比较（§5.5）。
 */
@Data
@TableName("cms_menu_item")
public class CmsMenuItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属菜单，见 cms_menu.id */
    private Long menuId;

    /** 父项（支持二级下拉），0 为一级项 */
    private Long parentId;

    /** 显示文字；为空则取所指向对象的名称 */
    private String label;

    /** category / content / type / tag / archive / author / url / custom */
    private String kind;

    /** 指向对象的 id（kind='content' / 'category' / 'tag' 等） */
    private Long refId;

    /** 指向对象的 code（kind='type' 时用） */
    private String refCode;

    /** kind='url' 时的外链地址 */
    private String url;

    private String target;

    private String rel;

    /** 是否可见（下线但保留结构） */
    private Integer visible;

    private Integer sort;

    @TableLogic
    private Integer deleted;
}
