-- =============================================================================
-- 站点级配置与发布选项
-- 契约：docs/static-publish.md §2.7、§12.1 第 4 条
--
-- 1. cms_site 补 §2.7 的 6 个新列，它们是 site 作用域封闭清单的一部分（§5.1）。
-- 2. cms_site_publish_option 存"这个站点要不要出这一类页面"等 47 个选项（§2.7 表）。
-- 3. domain 唯一索引写成部分唯一索引：多站点留空域名时不能互相冲突。
--
-- cms_site_publish_option 的 id 主键与 deleted 由迁移 20261002090014 补齐（§12.1 第 14 条）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- §2.7（2）cms_site 新增列
-- ---------------------------------------------------------------------------
alter table cms_site add column lang            varchar(16) not null default 'zh-CN';
alter table cms_site add column default_cover   varchar(255);
alter table cms_site add column og_image        varchar(255);
alter table cms_site add column theme           varchar(64);
alter table cms_site add column protocol        varchar(8)  not null default 'https';
alter table cms_site add column statistics_code text;

-- 域名唯一：空域名（还没绑定的站点）不参与唯一
create unique index uk_cms_site_domain on cms_site (domain) where deleted = 0 and domain is not null;

-- ---------------------------------------------------------------------------
-- §2.7（3）站点发布选项
-- 键一律写成 "命名空间.驼峰"，封闭清单见 §2.7；表外的键由应用层报错，不静默忽略。
-- ---------------------------------------------------------------------------
create table cms_site_publish_option (
    site_id     bigint      not null,
    option_code varchar(64) not null,
    value       text        not null default ''                -- 空串表示"未设置，用引擎默认"
);
create unique index uk_cms_site_publish_option on cms_site_publish_option (site_id, option_code);

comment on column cms_site.lang              is '站点主语言（zh-CN / en…），写进 <html lang> 与 hreflang';
comment on column cms_site.default_cover     is '站点默认图：内容 cover 为空时输出它';
comment on column cms_site.og_image          is '社交分享默认图';
comment on column cms_site.theme             is '当前主题名（§7.3 模板查找的第一层）；为空时用内置 _default 主题';
comment on column cms_site.protocol          is 'https / http，用于拼绝对 URL（sitemap / feed / canonical）';
comment on column cms_site.statistics_code   is '统计脚本，模板用 [field:site.statisticsCode/] 原样输出';
comment on table  cms_site_publish_option    is '站点发布选项：(site_id, option_code) → value，封闭清单见 §2.7';
