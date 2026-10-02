-- =============================================================================
-- 用户-站点绑定
--
-- 一行 = 一个用户可以切换访问的站点。右上角的站点切换器只列自己绑定到的站点；
-- 请求头 X-Site-Id 指到没绑定的站点时回落，见 module/cms/service/SiteService#accessibleSiteIds。
--
-- 与 sys_user_role 一样是纯关联表：没有审计字段，也没有逻辑删除。
-- admin 角色不受绑定限制（全部站点）；没绑定任何站点的用户只给默认站点。
-- 两种情况都不需要往这张表里写，所以已有用户不用回填。
-- =============================================================================

create table sys_user_site (
    user_id bigint not null,
    site_id bigint not null,
    primary key (user_id, site_id)
);

comment on table sys_user_site is '用户-站点绑定：用户可切换访问的站点';
