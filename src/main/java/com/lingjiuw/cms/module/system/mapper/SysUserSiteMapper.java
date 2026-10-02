package com.lingjiuw.cms.module.system.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** sys_user_site 关联表：用户可切换访问的站点 */
@Mapper
public interface SysUserSiteMapper {

    int insertBatch(@Param("userId") Long userId, @Param("siteIds") List<Long> siteIds);

    int deleteByUserId(@Param("userId") Long userId);

    int deleteBySiteId(@Param("siteId") Long siteId);

    List<Long> selectSiteIdsByUserId(@Param("userId") Long userId);

    /** 用户列表页一次取回多人的绑定，避免逐行查询 */
    List<UserSiteRow> selectByUserIds(@Param("userIds") List<Long> userIds);

    /** 查询结果行：用户-站点映射 */
    @Data
    class UserSiteRow {
        private Long userId;
        private Long siteId;
    }
}
