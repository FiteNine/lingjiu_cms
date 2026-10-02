package com.lingjiuw.cms.module.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** sys_user_role 关联表 */
@Mapper
public interface SysUserRoleMapper {

    int insertBatch(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    int deleteByUserId(@Param("userId") Long userId);

    int deleteByRoleId(@Param("roleId") Long roleId);

    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    long countByRoleId(@Param("roleId") Long roleId);
}
