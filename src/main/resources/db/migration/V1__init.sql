-- =============================================================================
-- V1: CMS 系统初始化表结构（PostgreSQL 16）
-- 约定：所有业务表统一审计字段 create_by/create_time/update_by/update_time/deleted
--      deleted 逻辑删除（0 未删 / 1 已删），唯一约束用部分索引实现（where deleted = 0）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 系统管理：用户 / 角色 / 菜单（权限）
-- ---------------------------------------------------------------------------
create table sys_user (
    id          bigserial    primary key,
    username    varchar(64)  not null,
    password    varchar(100) not null,
    nickname    varchar(64),
    email       varchar(128),
    phone       varchar(32),
    avatar      varchar(255),
    status      smallint     not null default 1,          -- 1 正常 / 0 停用
    remark      varchar(255),
    create_by   bigint,
    create_time timestamp    not null default now(),
    update_by   bigint,
    update_time timestamp    not null default now(),
    deleted     smallint     not null default 0
);
create unique index uk_sys_user_username on sys_user (username) where deleted = 0;
create index idx_sys_user_status on sys_user (status);

create table sys_role (
    id          bigserial    primary key,
    code        varchar(64)  not null,
    name        varchar(64)  not null,
    sort        int          not null default 0,
    status      smallint     not null default 1,
    remark      varchar(255),
    create_by   bigint,
    create_time timestamp    not null default now(),
    update_by   bigint,
    update_time timestamp    not null default now(),
    deleted     smallint     not null default 0
);
create unique index uk_sys_role_code on sys_role (code) where deleted = 0;

create table sys_menu (
    id          bigserial    primary key,
    parent_id   bigint       not null default 0,           -- 0 表示根节点
    name        varchar(64)  not null,
    path        varchar(128),
    component   varchar(128),
    icon        varchar(64),
    perms       varchar(128),                              -- 权限标识，如 cms:article:list
    type        varchar(8)   not null default 'MENU',      -- DIR 目录 / MENU 菜单 / BUTTON 按钮
    sort        int          not null default 0,
    visible     smallint     not null default 1,           -- 1 显示 / 0 隐藏
    status      smallint     not null default 1,
    remark      varchar(255),
    create_by   bigint,
    create_time timestamp    not null default now(),
    update_by   bigint,
    update_time timestamp    not null default now(),
    deleted     smallint     not null default 0
);
create index idx_sys_menu_parent on sys_menu (parent_id);

create table sys_user_role (
    user_id     bigint not null,
    role_id     bigint not null,
    primary key (user_id, role_id)
);

create table sys_role_menu (
    role_id     bigint not null,
    menu_id     bigint not null,
    primary key (role_id, menu_id)
);

-- ---------------------------------------------------------------------------
-- 字典
-- ---------------------------------------------------------------------------
create table sys_dict_type (
    id          bigserial    primary key,
    code        varchar(64)  not null,
    name        varchar(64)  not null,
    remark      varchar(255),
    create_by   bigint,
    create_time timestamp    not null default now(),
    update_by   bigint,
    update_time timestamp    not null default now(),
    deleted     smallint     not null default 0
);
create unique index uk_sys_dict_type_code on sys_dict_type (code) where deleted = 0;

create table sys_dict_item (
    id          bigserial    primary key,
    type_id     bigint       not null,
    label       varchar(64)  not null,
    value       varchar(64)  not null,
    sort        int          not null default 0,
    status      smallint     not null default 1,
    remark      varchar(255),
    create_by   bigint,
    create_time timestamp    not null default now(),
    update_by   bigint,
    update_time timestamp    not null default now(),
    deleted     smallint     not null default 0
);
create index idx_sys_dict_item_type on sys_dict_item (type_id);

-- ---------------------------------------------------------------------------
-- 操作日志
-- ---------------------------------------------------------------------------
create table sys_oper_log (
    id          bigserial    primary key,
    module      varchar(64),
    action      varchar(64),
    method      varchar(255),
    uri         varchar(255),
    params      text,
    ip          varchar(64),
    user_id     bigint,
    username    varchar(64),
    status      smallint     not null default 1,           -- 1 成功 / 0 失败
    error_msg   text,
    duration_ms int,
    create_by   bigint,
    create_time timestamp    not null default now(),
    update_by   bigint,
    update_time timestamp    not null default now(),
    deleted     smallint     not null default 0
);
create index idx_sys_oper_log_time on sys_oper_log (create_time desc);
create index idx_sys_oper_log_user on sys_oper_log (username);

-- ---------------------------------------------------------------------------
-- CMS：分类 / 文章 / 标签 / 媒体
-- ---------------------------------------------------------------------------
create table cms_category (
    id          bigserial    primary key,
    parent_id   bigint       not null default 0,
    name        varchar(64)  not null,
    slug        varchar(64),
    description varchar(255),
    cover       varchar(255),
    sort        int          not null default 0,
    status      smallint     not null default 1,
    create_by   bigint,
    create_time timestamp    not null default now(),
    update_by   bigint,
    update_time timestamp    not null default now(),
    deleted     smallint     not null default 0
);
create unique index uk_cms_category_slug on cms_category (slug) where deleted = 0;
create index idx_cms_category_parent on cms_category (parent_id);

create table cms_article (
    id            bigserial    primary key,
    category_id   bigint,
    title         varchar(255) not null,
    slug          varchar(255),
    summary       varchar(500),
    content       text,
    content_format varchar(16) not null default 'HTML',    -- HTML / MARKDOWN
    cover         varchar(255),
    author_id     bigint,
    author_name   varchar(64),
    status        varchar(16)  not null default 'DRAFT',   -- DRAFT / PUBLISHED / OFFLINE
    top           smallint     not null default 0,          -- 置顶
    recommend     smallint     not null default 0,          -- 推荐
    view_count    bigint       not null default 0,
    publish_time  timestamp,
    create_by     bigint,
    create_time   timestamp    not null default now(),
    update_by     bigint,
    update_time   timestamp    not null default now(),
    deleted       smallint     not null default 0
);
create unique index uk_cms_article_slug on cms_article (slug) where deleted = 0;
create index idx_cms_article_category on cms_article (category_id);
create index idx_cms_article_status on cms_article (status);
create index idx_cms_article_publish on cms_article (publish_time desc);

create table cms_tag (
    id          bigserial    primary key,
    name        varchar(64)  not null,
    slug        varchar(64),
    create_by   bigint,
    create_time timestamp    not null default now(),
    update_by   bigint,
    update_time timestamp    not null default now(),
    deleted     smallint     not null default 0
);
create unique index uk_cms_tag_slug on cms_tag (slug) where deleted = 0;

create table cms_article_tag (
    article_id  bigint not null,
    tag_id      bigint not null,
    primary key (article_id, tag_id)
);
create index idx_cms_article_tag_tag on cms_article_tag (tag_id);

create table cms_media (
    id           bigserial    primary key,
    name         varchar(255) not null,
    path         varchar(255) not null,                    -- 本地相对路径
    url          varchar(255) not null,                    -- 访问 URL
    size         bigint       not null default 0,
    mime_type    varchar(128),
    ext          varchar(16),
    storage_type varchar(16)  not null default 'LOCAL',    -- LOCAL / OSS（预留）
    create_by    bigint,
    create_time  timestamp    not null default now(),
    update_by    bigint,
    update_time  timestamp    not null default now(),
    deleted      smallint     not null default 0
);
create index idx_cms_media_time on cms_media (create_time desc);

-- ---------------------------------------------------------------------------
-- 注释
-- ---------------------------------------------------------------------------
comment on table  sys_user                 is '系统用户';
comment on table  sys_role                 is '角色';
comment on table  sys_menu                 is '菜单/权限';
comment on table  sys_dict_type            is '字典类型';
comment on table  sys_dict_item            is '字典项';
comment on table  sys_oper_log             is '操作日志';
comment on table  cms_category             is '内容分类';
comment on table  cms_article              is '文章';
comment on table  cms_tag                  is '标签';
comment on table  cms_article_tag          is '文章-标签关联';
comment on table  cms_media                is '媒体文件';
comment on column cms_article.status       is 'DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下线';
comment on column sys_menu.type            is 'DIR 目录 / MENU 菜单 / BUTTON 按钮';
