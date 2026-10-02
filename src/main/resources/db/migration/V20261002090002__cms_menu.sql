-- =============================================================================
-- 导航菜单：cms_menu / cms_menu_item
-- 契约：docs/static-publish.md §2.4、§12.1 第 2 条
--
-- 一行菜单 = 一组导航项（主导航 / 页脚 / 友情链接…）。菜单项支持二级下拉，
-- 可指向分类、内容、内容类型的列表页、标签、归档、作者或外链。
--
-- cms_menu_item 的 id 主键与 deleted 由迁移 20261002090014 补齐（§12.1 第 14 条）。
-- =============================================================================

create table cms_menu (
    id          bigserial   primary key,
    site_id     bigint      not null,
    code        varchar(64) not null,                          -- main / footer / friend…，站点内唯一
    name        varchar(64) not null,
    status      smallint    not null default 1,
    sort        int         not null default 0,
    create_by   bigint,
    create_time timestamp   not null default now(),
    update_by   bigint,
    update_time timestamp   not null default now(),
    deleted     smallint    not null default 0
);
create unique index uk_cms_menu_code on cms_menu (site_id, code) where deleted = 0;

create table cms_menu_item (
    menu_id   bigint      not null,                            -- cms_menu.id
    parent_id bigint      not null default 0,                  -- 0 为一级项，支持二级下拉
    label     varchar(64),                                     -- 为空则取所指向对象的名称
    kind      varchar(16) not null,                            -- category/content/type/tag/archive/author/url/custom
    ref_id    bigint,                                          -- kind 指向对象的 id
    ref_code  varchar(64),                                     -- kind='type' 用 ref_code
    url       varchar(500),                                    -- kind='url' 的外链地址
    target    varchar(16),                                     -- _blank
    rel       varchar(32),                                     -- nofollow
    visible   smallint    not null default 1,                  -- 下线但保留结构
    sort      int         not null default 0
);
create index idx_cms_menu_item_menu on cms_menu_item (menu_id, parent_id);

comment on table  cms_menu           is '导航菜单（一组导航项的容器）';
comment on table  cms_menu_item      is '导航菜单项（可二级；"哪一项是当前页"由引擎比对得出）';
comment on column cms_menu.code      is '菜单标识：main / footer / friend…，站点内唯一';
comment on column cms_menu_item.kind is 'category 分类 / content 某条内容 / type 类型列表页 / tag / archive / author / url 外链 / custom 纯占位父项';
