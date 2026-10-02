package com.lingjiuw.cms.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 用户拥有的全部权限标识（经 角色-菜单 授权得出） */
    List<String> selectPermsByUserId(@Param("userId") Long userId);
}
