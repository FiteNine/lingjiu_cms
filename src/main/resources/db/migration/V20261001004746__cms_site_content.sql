-- =============================================================================
-- 内容按站点归属
--
-- 1. cms_site 增加 is_default：系统有且仅有一个默认站点，请求没指定站点时一切读写都落在它上面。
-- 2. 分类 / 文章 / 标签 / 媒体增加 site_id：内容属于某个站点，后台右上角切到哪个站点就只看哪个站点的数据。
--    已有内容（V2 的种子数据）统一回填到默认站点。
-- 3. slug 唯一约束由「全库唯一」收紧为「站点内唯一」：两个站点可以各有一个 news 分类。
--
-- 请求到站点的解析见 common/site/SiteInterceptor：请求头 X-Site-Id > 请求参数 siteId > 默认站点。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 站点：is_default 与系统默认站点
-- ---------------------------------------------------------------------------
alter table cms_site add column is_default smallint not null default 0;

-- 至多一个默认站点，由数据库兜住这个不变量
create unique index uk_cms_site_default on cms_site (is_default) where is_default = 1 and deleted = 0;

-- 系统初始化必须有一个默认站点，没有就建出来
insert into cms_site (name, code, root_dir, description, status, is_default)
select '默认站点', 'default', 'default', '系统内置的默认站点，未指定站点时的内容归属', 1, 1
where not exists (select 1 from cms_site where code = 'default' and deleted = 0);

-- ---------------------------------------------------------------------------
-- 内容表：site_id
-- ---------------------------------------------------------------------------
alter table cms_category add column site_id bigint;
alter table cms_article  add column site_id bigint;
alter table cms_tag      add column site_id bigint;
alter table cms_media    add column site_id bigint;

-- 存量内容归到默认站点；默认站点缺失时这里会写入 null，紧接着的 set not null 会直接报错而不是留下脏数据
update cms_category set site_id = (select id from cms_site where code = 'default' and deleted = 0);
update cms_article  set site_id = (select id from cms_site where code = 'default' and deleted = 0);
update cms_tag      set site_id = (select id from cms_site where code = 'default' and deleted = 0);
update cms_media    set site_id = (select id from cms_site where code = 'default' and deleted = 0);

alter table cms_category alter column site_id set not null;
alter table cms_article  alter column site_id set not null;
alter table cms_tag      alter column site_id set not null;
alter table cms_media    alter column site_id set not null;

create index idx_cms_category_site on cms_category (site_id);
create index idx_cms_article_site  on cms_article (site_id);
create index idx_cms_tag_site      on cms_tag (site_id);
create index idx_cms_media_site    on cms_media (site_id);

-- slug 改为站点内唯一
drop index uk_cms_category_slug;
create unique index uk_cms_category_slug on cms_category (site_id, slug) where deleted = 0;
drop index uk_cms_article_slug;
create unique index uk_cms_article_slug on cms_article (site_id, slug) where deleted = 0;
drop index uk_cms_tag_slug;
create unique index uk_cms_tag_slug on cms_tag (site_id, slug) where deleted = 0;

-- ---------------------------------------------------------------------------
-- 注释
-- ---------------------------------------------------------------------------
comment on column cms_site.is_default      is '1 系统默认站点：有且仅有一个，不允许删除';
comment on column cms_category.site_id     is '所属站点，见 cms_site.id';
comment on column cms_article.site_id      is '所属站点，见 cms_site.id';
comment on column cms_tag.site_id          is '所属站点，见 cms_site.id';
comment on column cms_media.site_id        is '所属站点，见 cms_site.id';
