package com.lingjiuw.cms.module.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** sys_role_menu 关联表 */
@Mapper
public interface SysRoleMenuMapper {

    int deleteByRoleId(@Param("roleId") Long roleId);

    int deleteByMenuId(@Param("menuId") Long menuId);

    int insertBatch(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);

    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
}
