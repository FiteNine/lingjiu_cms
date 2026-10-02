package com.lingjiuw.cms.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingjiuw.cms.module.system.entity.SysRole;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    List<SysRole> selectByUserId(@Param("userId") Long userId);

    /** 批量查用户-角色映射；userIds 不可为 null 或空集合（空 IN 会生成非法 SQL） */
    List<UserRoleRow> selectRolesByUserIds(@Param("userIds") List<Long> userIds);

    /** 查询结果行：用户-角色映射 */
    @Data
    class UserRoleRow {
        private Long userId;
        private Long id;
        private String code;
        private String name;
    }
}
