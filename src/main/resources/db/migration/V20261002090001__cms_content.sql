-- =============================================================================
-- 内容模型地基：内容类型 / 字段定义 / 内容 / 字段索引表 / 内容分类 / 内容标签
-- 契约：docs/static-publish.md §2.1–§2.5、§12.1 第 1 条
--
-- 约定沿用 V1__init.sql：bigserial 主键、审计字段 create_by/create_time/update_by/
-- update_time/deleted、唯一约束一律写成部分唯一索引（where deleted = 0）。
-- 时间列用 timestamp（不带时区），与现有 6 张表保持一致，见迁移末尾的说明。
--
-- 本文件建的是"按 §2 描述的原样"结构；纯明细表（cms_content_index /
-- cms_content_category / cms_content_tag）的主键与 deleted 由迁移
-- 20261002090014 统一补齐（§12.1 第 14 条），本文件不抢跑。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- §2.1 内容类型
-- ---------------------------------------------------------------------------
create table cms_content_type (
    id                 bigserial    primary key,
    site_id            bigint       not null,
    code               varchar(64)  not null,                 -- 站点内唯一，仅小写字母数字下划线
    name               varchar(64)  not null,                 -- 后台显示名
    kind               varchar(16)  not null default 'CONTENT', -- CONTENT / SINGLE / TREE
    hierarchical       smallint     not null default 0,       -- 是否父子层级（TREE 隐含为 1）
    detail_url_pattern varchar(255),                          -- 占位符白名单见 §7.1.1
    list_url_pattern   varchar(255),                          -- 为空表示该类型不出列表页
    detail_template    varchar(255),                          -- 为空则用主题默认（§7.3）
    list_template      varchar(255),
    paginate_body      varchar(64),                           -- 正文分页字段 code，为空表示不拆正文
    sort_field         varchar(64),                           -- 为空时引擎按 publishTime desc（§6.3）
    sort_order         varchar(8),                            -- asc / desc
    per_page           int          not null default 20,
    seo_title_field    varchar(64),
    seo_desc_field     varchar(64),
    options            jsonb,                                 -- 页面类型开关与显示期选项（§7.2）
    status             smallint     not null default 1,
    sort               int          not null default 0,
    create_by          bigint,
    create_time        timestamp    not null default now(),
    update_by          bigint,
    update_time        timestamp    not null default now(),
    deleted            smallint     not null default 0
);
create unique index uk_cms_content_type_code on cms_content_type (site_id, code) where deleted = 0;

-- ---------------------------------------------------------------------------
-- §2.2 字段定义
--
-- site_id 不在 §2.2 的列表里，但类型是站点内定义（§2.1 "类型由站点各自定义"、
-- code "站点内唯一"），两个站点可以各有一个 article 类型、各有一个 brand 字段，
-- 唯一键必须带 site_id，否则第二个站点定义同名字段会直接冲突。
-- ---------------------------------------------------------------------------
create table cms_field (
    id            bigserial    primary key,
    site_id       bigint       not null,
    type_code     varchar(64)  not null,                      -- 所属类型（cms_content_type.code）
    code          varchar(64)  not null,                      -- 模板里 [field:xxx/]；六个保留名不得使用（§5.1）
    label         varchar(64)  not null,
    field_type    varchar(16)  not null,                      -- TEXT…RELATION 共 19 种（§2.2）
    formatter     varchar(255),                               -- 输出格式化器，空为默认（§2.2）
    raw           smallint     not null default 0,            -- 是否原样输出（富文本 = 1）
    required      smallint     not null default 0,
    default_value varchar(500),
    options       varchar(1000),                              -- ENUM / ENUM_MULTI 的 "值:标签" 逗号分隔
    searchable    smallint     not null default 0,            -- 是否进静态搜索索引（§7.5）
    indexed       smallint     not null default 0,            -- 是否进 cms_content_index（§2.5）
    cross_site    smallint     not null default 0,            -- RELATION 字段的 crossSite=1（§2.2、§11.5）
    help          varchar(255),
    sort          int          not null default 0,
    create_by     bigint,
    create_time   timestamp    not null default now(),
    update_by     bigint,
    update_time   timestamp    not null default now(),
    deleted       smallint     not null default 0
);
create unique index uk_cms_field_code on cms_field (site_id, type_code, code) where deleted = 0;

-- ---------------------------------------------------------------------------
-- §2.3 内容
-- ---------------------------------------------------------------------------
create table cms_content (
    id              bigserial    primary key,
    site_id         bigint       not null,
    type_code       varchar(64)  not null,
    parent_id       bigint       not null default 0,          -- 0 为根
    slug            varchar(255),
    title           varchar(255) not null,
    summary         varchar(500),
    cover           varchar(255),
    status          varchar(16)  not null default 'DRAFT',    -- DRAFT / PUBLISHED / OFFLINE
    sort            int          not null default 0,
    top             smallint     not null default 0,
    recommend       smallint     not null default 0,
    publish_time    timestamp,
    author_id       bigint,                                   -- 指向 type_code='author' 的内容项
    author_name     varchar(64),                              -- 冗余快照，引擎写入
    view_count      bigint       not null default 0,
    content_format  varchar(16)  not null default 'RICHTEXT',  -- RICHTEXT / MARKDOWN
    seo_title       varchar(255),
    seo_description varchar(500),
    seo_keywords    varchar(255),
    data            jsonb,                                    -- 自定义字段值
    content         text,
    content_html    text,                                     -- MARKDOWN 预渲染结果
    content_toc     jsonb,                                    -- 正文标题树（§5.2.5）
    word_count      int          not null default 0,
    create_by       bigint,
    create_time     timestamp    not null default now(),
    update_by       bigint,
    update_time     timestamp    not null default now(),
    deleted         smallint     not null default 0
);

-- §2.3：slug 站点内 + 类型内唯一；层级内容（chapter 一类）同一 parent_id 下唯一。
-- 两个部分唯一索引按"是否处于某个父之下"分工，既保住非层级类型的类型内唯一，
-- 又允许两本书各有一个同 slug 的章节。parent_id = 0 且 slug 为 null 的行不参与
-- （PostgreSQL 的唯一索引本来就不约束全 null 的键，这里再加一层显式条件）。
create unique index uk_cms_content_slug on cms_content (site_id, type_code, slug)
    where deleted = 0 and parent_id = 0;
create unique index uk_cms_content_slug_parent on cms_content (site_id, type_code, parent_id, slug)
    where deleted = 0 and parent_id > 0;

-- §2.3：SINGLE 类型在每个站点至多一条。
-- 局限如实记录：部分索引的谓词不能子查询 cms_content_type.kind，所以这里只能对内置
-- 单页类型 code='single' 兜底；自定义的 SINGLE 类型由应用层校验（同 §7.2.1 的计划期校验）。
create unique index uk_cms_content_single on cms_content (site_id, type_code)
    where deleted = 0 and type_code = 'single';

create index idx_cms_content_parent  on cms_content (parent_id);
create index idx_cms_content_author  on cms_content (author_id);
-- §2.5：跨类型聚合（type='all'）只走公共列 site_id / status / publish_time
create index idx_cms_content_publish on cms_content (site_id, status, publish_time desc);

-- ---------------------------------------------------------------------------
-- §2.5 字段索引表（不做动态 DDL 的代价与对策）
-- ---------------------------------------------------------------------------
create table cms_content_index (
    site_id    bigint       not null,
    content_id bigint       not null,
    type_code  varchar(64)  not null,
    field_code varchar(64)  not null,
    value_key  varchar(200) not null,                          -- ENUM_MULTI 每个取值一行
    value_type varchar(16)  not null,
    num_value  numeric,
    str_value  varchar(200),
    time_value timestamp
);
create unique index uk_cms_content_index on cms_content_index (content_id, field_code, value_key);
create index idx_cms_content_index_num  on cms_content_index (site_id, type_code, field_code, num_value);
create index idx_cms_content_index_str  on cms_content_index (site_id, type_code, field_code, str_value);
create index idx_cms_content_index_time on cms_content_index (site_id, type_code, field_code, time_value);

-- ---------------------------------------------------------------------------
-- §2.4 内容 ↔ 分类（多对多，带 dimension）/ 内容 ↔ 标签
-- ---------------------------------------------------------------------------
create table cms_content_category (
    content_id  bigint      not null,
    category_id bigint      not null,
    dimension   varchar(16) not null default 'primary'          -- primary / secondary
);
create unique index uk_cms_content_category on cms_content_category (content_id, category_id, dimension);
-- §2.3：主分类恰一个，它决定 categoryUrl、面包屑与 canonical
create unique index uk_cms_content_category_primary on cms_content_category (content_id)
    where dimension = 'primary';
create index idx_cms_content_category_category on cms_content_category (category_id);

create table cms_content_tag (
    content_id bigint not null,
    tag_id     bigint not null
);
create unique index uk_cms_content_tag on cms_content_tag (content_id, tag_id);
create index idx_cms_content_tag_tag on cms_content_tag (tag_id);

-- ---------------------------------------------------------------------------
-- 注释
-- ---------------------------------------------------------------------------
comment on table  cms_content_type        is '内容类型（站点自助定义）';
comment on table  cms_field               is '内容类型的字段定义';
comment on table  cms_content             is '内容（所有内容类型的统一存储）';
comment on table  cms_content_index       is '字段索引表：indexed=1 的自定义字段每个取值一行';
comment on table  cms_content_category    is '内容-分类关联（dimension: primary / secondary）';
comment on table  cms_content_tag         is '内容-标签关联（替代 cms_article_tag）';
comment on column cms_content_type.kind   is 'CONTENT 有列表+详情 / SINGLE 单页全站仅一份 / TREE 层级内容';
comment on column cms_content.content_format is 'RICHTEXT（入库清洗）/ MARKDOWN（入库预渲染），见 §5.2';
comment on column cms_content_index.value_key is 'ENUM_MULTI / TAGS 等多值字段的单个取值，标量字段恒为 ''default''';
comment on column cms_content_category.dimension is 'primary 主分类每内容至多一行 / secondary 副分类';
