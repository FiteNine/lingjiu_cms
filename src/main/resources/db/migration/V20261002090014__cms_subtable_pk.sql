-- =============================================================================
-- 明细表补齐既有工程约定：id 主键、deleted 逻辑删除列、部分唯一索引
-- 契约：docs/static-publish.md §12.1 第 14 条
--
-- 迁移 1 / 2 / 4 按 §2.4 / §2.5 / §2.7 的原样建了这几张明细表（只有关联列），
-- 本文件统一补上 V1__init.sql 的约定：id 主键（MyBatis-Plus 的 deleteById 等
-- 内置方法需要它）、deleted 逻辑删除列，并把唯一索引改成 where deleted = 0 的部分索引。
--
-- 涉及：cms_content_index（迁移 1）、cms_content_category（迁移 1）、
--       cms_content_tag（迁移 1）、cms_menu_item（迁移 2）、
--       cms_site_publish_option（迁移 4）。
-- cms_redirect / cms_form_field 属另外两个迁移文件的范围，不在本文件里。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- cms_content_index
-- ---------------------------------------------------------------------------
alter table cms_content_index add column id      bigserial primary key;
alter table cms_content_index add column deleted smallint  not null default 0;
drop index uk_cms_content_index;
create unique index uk_cms_content_index on cms_content_index (content_id, field_code, value_key)
    where deleted = 0;

-- ---------------------------------------------------------------------------
-- cms_content_category
-- ---------------------------------------------------------------------------
alter table cms_content_category add column id      bigserial   primary key;
alter table cms_content_category add column deleted smallint    not null default 0;
drop index uk_cms_content_category;
create unique index uk_cms_content_category on cms_content_category (content_id, category_id, dimension)
    where deleted = 0;
-- §2.3：主分类恰一个
drop index uk_cms_content_category_primary;
create unique index uk_cms_content_category_primary on cms_content_category (content_id)
    where dimension = 'primary' and deleted = 0;

-- ---------------------------------------------------------------------------
-- cms_content_tag
-- ---------------------------------------------------------------------------
alter table cms_content_tag add column id      bigserial primary key;
alter table cms_content_tag add column deleted smallint  not null default 0;
drop index uk_cms_content_tag;
create unique index uk_cms_content_tag on cms_content_tag (content_id, tag_id) where deleted = 0;

-- ---------------------------------------------------------------------------
-- cms_menu_item
-- 菜单项没有天然唯一键（同一父项下可以有两项同名、同 kind），因此只补主键与
-- deleted，不造一个假唯一约束；排序与遍历走迁移 2 建的 (menu_id, parent_id) 索引。
-- ---------------------------------------------------------------------------
alter table cms_menu_item add column id      bigserial primary key;
alter table cms_menu_item add column deleted smallint  not null default 0;

-- ---------------------------------------------------------------------------
-- cms_site_publish_option
-- ---------------------------------------------------------------------------
alter table cms_site_publish_option add column id      bigserial   primary key;
alter table cms_site_publish_option add column deleted smallint    not null default 0;
drop index uk_cms_site_publish_option;
create unique index uk_cms_site_publish_option on cms_site_publish_option (site_id, option_code)
    where deleted = 0;
