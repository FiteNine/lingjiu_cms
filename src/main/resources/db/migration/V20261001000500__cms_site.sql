-- =============================================================================
-- V4: 站点管理
--
-- 一行 = 一个站点。root_dir 存的是**相对** `cms.site.root-dir` 的路径，例如 lingjiuw.cn，
-- 后端把它解析成服务器上的绝对目录，用于存放该站点的网站文件。
-- 存相对路径而不是绝对路径：整个站点根目录搬迁时数据库不用改。
--
-- 后台的「选择/新建文件夹」只能在这个根目录内活动，绝对路径与 .. 上跳一律拒绝，
-- 见 module/cms/service/SiteService#resolve。
-- =============================================================================

create table cms_site (
    id              bigserial    primary key,
    name            varchar(64)  not null,                 -- 站点名称
    code            varchar(64)  not null,                 -- 站点标识
    domain          varchar(255),                          -- 站点域名
    logo            varchar(255),                          -- 站点 Logo
    description     varchar(500),                          -- 站点描述
    keywords        varchar(255),                          -- SEO 关键词
    seo_description varchar(500),                          -- SEO 描述
    root_dir        varchar(255) not null,                 -- 站点目录，相对 cms.site.root-dir，如 lingjiuw.cn
    icp             varchar(64),                           -- ICP 备案号
    contact_phone   varchar(32),                           -- 联系电话
    contact_email   varchar(128),                          -- 联系邮箱
    status          smallint     not null default 1,       -- 1 启用 / 0 停用
    create_by       bigint,
    create_time     timestamp    not null default now(),
    update_by       bigint,
    update_time     timestamp    not null default now(),
    deleted         smallint     not null default 0
);
create unique index uk_cms_site_code on cms_site (code) where deleted = 0;

-- ---------------------------------------------------------------------------
-- 菜单 / 权限
-- ---------------------------------------------------------------------------
insert into sys_menu (id, parent_id, name, path, component, icon, perms, type, sort) values
    (40,  0,  '站点管理', '/sites', 'sites/index', 'Monitor', 'cms:site:list',   'MENU',   4),
    (401, 40, '站点新增', null, null, null, 'cms:site:add',    'BUTTON', 1),
    (402, 40, '站点编辑', null, null, null, 'cms:site:edit',   'BUTTON', 2),
    (403, 40, '站点删除', null, null, null, 'cms:site:delete', 'BUTTON', 3);

-- 站点是全局配置，只授权给超级管理员（内容编辑角色不涉及）
insert into sys_role_menu (role_id, menu_id)
select 1, id from sys_menu where id in (40, 401, 402, 403);

select setval('sys_menu_id_seq', (select max(id) from sys_menu));

-- ---------------------------------------------------------------------------
-- 注释
-- ---------------------------------------------------------------------------
comment on table  cms_site           is '站点';
comment on column cms_site.root_dir  is '站点目录：相对 cms.site.root-dir 的路径，如 lingjiuw.cn';
comment on column cms_site.status    is '1 启用 / 0 停用';
