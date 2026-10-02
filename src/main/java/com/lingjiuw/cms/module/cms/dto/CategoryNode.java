package com.lingjiuw.cms.module.cms.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 分类树节点 */
@Data
public class CategoryNode {

    private Long id;
    private Long parentId;
    private String name;
    private String slug;
    private String description;
    private String cover;
    private Integer sort;
    private Integer status;
    /** 计数不是 id：覆盖全局 Long→String，按数值输出（前端 CategoryNode.contentCount 声明为 number） */
    @JsonSerialize(using = LongNumberSerializer.class)
    private Long contentCount;
    private List<CategoryNode> children = new ArrayList<>();
}
