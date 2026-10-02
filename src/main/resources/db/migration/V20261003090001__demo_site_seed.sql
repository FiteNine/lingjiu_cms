-- =============================================================================
-- demo 站演示数据（静态发布引擎的联调素材）
-- 契约：docs/static-publish.md §2.1–§2.7、§7.1.1、§7.2.1、§7.5
--
-- 这个文件只给 code='demo' 的站点灌演示内容，不碰其它站点。目标是让另一条线上
-- 正在写的主题（cms_site.theme='mint'、站点目录 sites/demo）有真实可渲染的数据：
-- 分页要有第二页、月/年归档要有多个分桶、标签页要有点击量、筛选项要真的有结果。
--
-- 三条写法约定，与迁移 20261002090011 保持一致：
--   1. 先 insert ... select ... where not exists 补缺，再 update 覆盖成 demo 要的值。
--      迁移 11 已经给**每个站点**建好了 article / single / author 三个内置类型、一个
--      main 菜单与 55 个发布选项，所以这三样绝不能裸 INSERT——会直接撞部分唯一索引。
--   2. 内容一律先 insert 再用 slug 反查 id 做后续 update，文件里不写死自增 id，
--      这样即使 cms_content 上先有了别的数据也不会撞号。
--   3. 所有 INSERT 都写全列名；jsonb 列的字符串字面量一律带 ::jsonb（实体侧映射成
--      String，PostgreSQL 不会把 varchar 隐式转成 jsonb）。
--
-- demo 站点的 55 个发布选项里，本文件只覆盖"主题会读、且必须不是空表"的那批开关
-- （page.* 全开、url.* 与 facets/feed/search 的取值），其余保持迁移 11 的默认值。
-- 站点的 root_dir 固定为 demo（对应 backend/sites/demo）——本文件不建目录。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 站点：code='demo'
--
-- 站点可能已经存在（迁移 11 的播种是按 cms_site 遍历的，人工建站也会落一行），
-- 因此先判存插入，再用 update 把主题指向正在并行写的 mint。
-- protocol/domain 是给 sitemap / feed / canonical 拼绝对地址用的。
-- ---------------------------------------------------------------------------
insert into cms_site (name, code, domain, description, keywords, seo_description,
                      root_dir, icp, contact_phone, contact_email,
                      lang, theme, protocol, status, is_default)
select '灵久演示站', 'demo', 'demo.lingjiuw.local',
       '静态发布引擎的演示站点：资讯、产品、书籍章节与作者一套齐全',
       '静态站点,内容管理,Java,模板引擎',
       '灵久 CMS 演示站点，用来验证内容类型、字段索引、筛选落地页与静态发布产物',
       'demo', '京ICP备2026000000号', '010-88880000', 'demo@lingjiuw.local',
       'zh-CN', 'mint', 'https', 1, 0
 where not exists (select 1 from cms_site where code = 'demo' and deleted = 0);

update cms_site
   set name            = '灵久演示站',
       domain          = 'demo.lingjiuw.local',
       description     = '静态发布引擎的演示站点：资讯、产品、书籍章节与作者一套齐全',
       keywords        = '静态站点,内容管理,Java,模板引擎',
       seo_description = '灵久 CMS 演示站点，用来验证内容类型、字段索引、筛选落地页与静态发布产物',
       root_dir        = 'demo',
       icp             = '京ICP备2026000000号',
       contact_phone   = '010-88880000',
       contact_email   = 'demo@lingjiuw.local',
       lang            = 'zh-CN',
       theme           = 'mint',
       protocol        = 'https',
       status          = 1
 where code = 'demo' and deleted = 0;

-- ---------------------------------------------------------------------------
-- 2. 发布选项（§2.7 的封闭清单）
--
-- 迁移 11 已经给每个站点写了 55 行，这里只补缺 + 覆盖值：
--   * facets.combos 里那个 "brand-acme+region-huadong" 是 §7.4 的手工配置组合，
--     第 6 节会保证它真的有内容（acme 且 huadong 的产品）。
--   * pages.static 在迁移 11 里只有 thanks 一项，这里覆盖成 thanks + rank 两项；
--     rank 声明了 query（§7.2.1 第 11 行：声明了 query 的静态页恰有一个分页主体），
--     URL 带 {n}，材料来自第 8 节填的 view_count_week。
-- ---------------------------------------------------------------------------
with opts(option_code, value) as (
    values
        ('page.category',         '1'),
        ('page.tag',              '1'),
        ('page.taglist',          '1'),
        ('page.archive',          '1'),
        ('page.author',           '1'),
        ('page.facet',            '1'),
        ('page.search',           '1'),
        ('page.feed',             '1'),
        ('page.redirect',         '1'),
        ('url.list',              '/{categoryPath}/page-{n}/'),
        ('url.tag',               '/tag/{tagSlug}/'),
        ('url.tags',              '/tags/'),
        ('url.archive',           '/archive/{year}/{month}/'),
        ('url.search',            '/search/'),
        ('url.home',              '/page-{n}/'),
        ('url.facet',             '/f/{facetPath}/'),
        ('url.thanks',            '/thanks/'),
        ('facets.combos',         '["brand-acme+region-huadong"]'),
        ('facets.maxPages',       '500'),
        ('facets.cardinality',    '50'),
        ('feed.format',           'rss'),
        ('feed.size',             '20'),
        ('feed.types',            '["article"]'),
        ('feed.includeBody',      '1'),
        ('sitemap.shardSize',     '10000'),
        ('search.mode',           'static'),
        ('search.staticMax',      '50000'),
        ('search.bodyChars',      '1000'),
        ('index.shardSize',       '2000'),
        ('seo.paginatedIndex',    '0'),
        ('seo.facetIndex',        '0'),
        ('seo.noindexTypes',      '[]'),
        ('pager.labels',          '首页,上一页,下一页,末页'),
        ('toc.levels',            'h2,h3'),
        ('reading.speed',         '400'),
        ('i18n.alternates',       '[]'),
        ('media.derive',          '320,768,1280'),
        ('media.host',            ''),
        ('publish.mode',          'incremental'),
        ('publish.keepReleases',  '3'),
        ('publish.syncTarget',    ''),
        ('publish.cron',          '0 3 * * *'),
        ('publish.threads',       ''),
        ('publish.pageTimeout',   '10'),
        ('publish.debounce',      '5'),
        ('publish.rankCron',      '0 * * * *'),
        ('publish.preview',       '0'),
        ('publish.strict',        '0'),
        ('publish.expireRedirect', '0'),
        ('page.tagMinCount',      '1'),
        ('neighbor.limit',        '1'),
        ('comment.moderate',      '1'),
        ('comment.snapshot',      '1'),
        ('comment.snapshotSize',  '20'),
        ('pages.static',
         '[{"code":"thanks","url":"/thanks/","template":"thanks.html","type":"single"},'
         ' {"code":"rank","url":"/rank/page-{n}/","template":"rank.html","type":"list",'
         ' "query":{"type":"article","orderby":"viewCount desc","row":10}}]')
),
ins as (
    insert into cms_site_publish_option (site_id, option_code, value)
    select s.id, o.option_code, o.value
      from cms_site s
      cross join opts o
     where s.code = 'demo' and s.deleted = 0
       and not exists (select 1 from cms_site_publish_option p
                        where p.site_id = s.id and p.option_code = o.option_code and p.deleted = 0)
    returning option_code
)
update cms_site_publish_option p
   set value = o.value
  from cms_site s, opts o
 where s.code = 'demo' and s.deleted = 0
   and p.site_id = s.id
   and p.option_code = o.option_code
   and p.deleted = 0
   and p.value is distinct from o.value;

-- ---------------------------------------------------------------------------
-- 3. 内容类型（§2.1）
--
-- 迁移 11 的播种只覆盖"它执行那一刻已存在的站点"（遍历 cms_site），而 demo 站点是
-- 本文件第 1 节才建出来的，因此它**没有** article / single / author 这三个内置类型。
-- 所以这里连内置类型一起补（判存用 (site_id, code) + deleted = 0），再统一 update
-- 成 demo 主题要的模板与分页参数（article 的 list_url_pattern 从内置的
-- /{typeCode}/page-{n}/ 收敛成 /news/page-{n}/，与 url.list 的分类索引页错开）。
-- 若 demo 站点是人工先建的、迁移 11 已经播过种，这个 insert 会因为判存条件一行不插。
--
-- paginate_body 只有 chapter 非空（§2.1：正文分页必须配 {n} 的 detail_url_pattern），
-- 因此 article 的正文里不出现 <!--cms:page-->，章节正文里有。
-- ---------------------------------------------------------------------------
insert into cms_content_type (site_id, code, name, kind, hierarchical,
                              detail_url_pattern, list_url_pattern, per_page, status, sort)
select s.id, v.code, v.name, v.kind, v.hierarchical,
       v.detail_url_pattern, v.list_url_pattern, v.per_page, 1, v.sort
  from cms_site s
  cross join (values
      ('article', '文章',     'CONTENT', 0, '/{categoryPath}/{slug}.html',        '/news/page-{n}/',      6,  1),
      ('single',  '单页',     'SINGLE',  0, '/{slug}/',                           null,                   20, 2),
      ('author',  '作者',     'CONTENT', 0, '/author/{slug}.html',                '/author/page-{n}/',    12, 3),
      ('product', '产品',     'CONTENT', 0, '/product/{slug}.html',              '/product/page-{n}/',   6,  4),
      ('book',    '书籍',     'TREE',    1, '/book/{slug}.html',                 '/book/page-{n}/',      12, 5),
      ('chapter', '章节',     'TREE',    1, '/book/{parentSlug}/{slug}.html',    null,                   50, 6),
      ('about',   '关于我们', 'SINGLE',  0, '/about/',                           null,                   20, 7),
      ('contact', '联系我们', 'SINGLE',  0, '/contact/',                         null,                   20, 8)
  ) as v(code, name, kind, hierarchical, detail_url_pattern, list_url_pattern, per_page, sort)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content_type t
                    where t.site_id = s.id and t.code = v.code and t.deleted = 0);

update cms_content_type t
   set name               = v.name,
       kind               = v.kind,
       hierarchical       = v.hierarchical,
       detail_url_pattern = v.detail_url_pattern,
       list_url_pattern   = v.list_url_pattern,
       per_page           = v.per_page,
       sort_field         = v.sort_field,
       sort_order         = v.sort_order,
       paginate_body      = v.paginate_body,
       detail_template    = v.detail_template,
       list_template      = v.list_template,
       status             = 1,
       sort               = v.sort
  from cms_site s
  cross join (values
      ('article', '文章',     'CONTENT', 0, '/{categoryPath}/{slug}.html',        '/news/page-{n}/',      6,  'publishTime', 'desc', null,      'article_detail.html', 'article_list.html', 1),
      ('product', '产品',     'CONTENT', 0, '/product/{slug}.html',               '/product/page-{n}/',   6,  'publishTime', 'desc', null,      'product_detail.html', 'product_list.html', 2),
      ('book',    '书籍',     'TREE',    1, '/book/{slug}.html',                  '/book/page-{n}/',      12, 'publishTime', 'desc', null,      'book_detail.html',    'book_list.html',    3),
      ('chapter', '章节',     'TREE',    1, '/book/{parentSlug}/{slug}.html',     null,                   50, 'sort',        'asc',  'content', 'chapter_detail.html', null,                4),
      ('author',  '作者',     'CONTENT', 0, '/author/{slug}.html',                '/author/page-{n}/',    12, 'publishTime', 'desc', null,      'author_detail.html',  null,                5),
      ('about',   '关于我们', 'SINGLE',  0, '/about/',                            null,                   20, 'publishTime', 'desc', null,      'about.html',          null,                6),
      ('contact', '联系我们', 'SINGLE',  0, '/contact/',                          null,                   20, 'publishTime', 'desc', null,      'contact.html',        null,                7),
      ('single',  '单页',     'SINGLE',  0, '/{slug}/',                           null,                   20, 'publishTime', 'desc', null,      null,                  null,                8)
  ) as v(code, name, kind, hierarchical, detail_url_pattern, list_url_pattern, per_page,
         sort_field, sort_order, paginate_body, detail_template, list_template, sort)
 where s.code = 'demo' and s.deleted = 0
   and t.site_id = s.id
   and t.code = v.code
   and t.deleted = 0;

-- ---------------------------------------------------------------------------
-- 4. 字段定义（§2.2 的 19 种 field_type 里用到 13 种）
--
-- indexed=1 的字段会在第 7 节逐条写进 cms_content_index（where / orderby / facet 只读
-- 索引表），其中 relatedProducts 必须 indexed=1——主题在详情页用
-- {cms:list relate='field:relatedProducts'}，而 relate='field:<code>' 在编译期要求
-- 该字段勾了"可筛选"（§6.3 / §4.5 第 6 条），漏勾会直接报 E2007。
-- searchable=1 的进静态搜索索引的正文（§7.5 只收 TEXT / TEXTAREA / RICHTEXT /
-- MARKDOWN / ENUM）；raw=1 的按富文本原样输出。
-- 字段 code 避开六个保留名（site / channel / page / param / query / item，§5.1）。
-- ---------------------------------------------------------------------------
insert into cms_field (site_id, type_code, code, label, field_type, raw, searchable, indexed, sort)
select s.id, v.type_code, v.code, v.label, v.field_type, v.raw, v.searchable, v.indexed, v.sort
  from cms_site s
  cross join (values
      -- article：行业站资讯的主题词全走自定义字段，筛选页靠 brand / region / price / weight / featured
      ('article', 'brand',          '品牌',     'TEXT',       0, 1, 1, 1),
      ('article', 'region',         '区域',     'ENUM',       0, 0, 1, 2),
      ('article', 'price',          '参考价',   'DECIMAL',    0, 0, 1, 3),
      ('article', 'weight',         '重量',     'INT',        0, 0, 1, 4),
      ('article', 'featured',       '首页推荐', 'BOOL',       0, 0, 1, 5),
      ('article', 'sku',            '货号',     'TEXT',       0, 0, 0, 6),
      ('article', 'specs',          '规格参数', 'JSON',       0, 0, 0, 7),
      ('article', 'gallery',        '图集',     'IMAGES',     0, 0, 0, 8),
      ('article', 'download',       '资料下载', 'FILE',       0, 0, 0, 9),
      ('article', 'relatedProducts','相关产品', 'RELATION',   0, 1, 1, 10),
      ('article', 'readingLevel',   '阅读难度', 'ENUM_MULTI', 0, 0, 1, 11),
      ('article', 'tags',           '标签',     'TAGS',       0, 0, 1, 12),
      -- product
      ('product', 'brand',          '品牌',     'ENUM',       0, 0, 1, 1),
      ('product', 'region',         '区域',     'ENUM',       0, 0, 1, 2),
      ('product', 'price',          '价格',     'DECIMAL',    0, 0, 1, 3),
      ('product', 'stock',          '库存',     'INT',        0, 0, 1, 4),
      ('product', 'featured',       '推荐位',   'BOOL',       0, 0, 1, 5),
      ('product', 'specs',          '规格参数', 'JSON',       0, 0, 0, 6),
      ('product', 'gallery',        '图集',     'IMAGES',     0, 0, 0, 7),
      ('product', 'download',       '下载资料', 'FILE',       0, 0, 0, 8),
      ('product', 'relatedProducts','相关产品', 'RELATION',   0, 0, 1, 9),
      -- book / chapter
      ('book',    'author',         '作者',     'TEXT',       0, 1, 0, 1),
      ('book',    'bookStatus',     '连载状态', 'ENUM',       0, 0, 1, 2),
      ('book',    'tags',           '标签',     'TAGS',       0, 0, 1, 3),
      ('chapter', 'volume',         '分卷',     'TEXT',       0, 0, 0, 1),
      -- author
      ('author',  'honor',          '荣誉',     'TEXT',       0, 0, 0, 1),
      ('author',  'bio',            '简介',     'TEXTAREA',   1, 1, 0, 2),
      ('author',  'avatar',         '头像',     'IMAGE',      0, 0, 0, 3),
      -- about / contact：单页只放一个副标题
      ('about',   'subtitle',       '副标题',   'TEXT',       0, 0, 0, 1),
      ('contact', 'subtitle',       '副标题',   'TEXT',       0, 0, 0, 1)
  ) as v(type_code, code, label, field_type, raw, searchable, indexed, sort)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_field f
                    where f.site_id = s.id and f.type_code = v.type_code
                      and f.code = v.code and f.deleted = 0);

update cms_field f
   set label      = v.label,
       field_type = v.field_type,
       options    = v.options,
       raw        = v.raw,
       searchable = v.searchable,
       indexed    = v.indexed,
       sort       = v.sort
  from cms_site s
  cross join (values
      ('article', 'brand',          '品牌',     'TEXT',       null,                                                     0, 1, 1, 1),
      ('article', 'region',         '区域',     'ENUM',       'huadong:华东,huanan:华南,huabei:华北',                    0, 0, 1, 2),
      ('article', 'price',          '参考价',   'DECIMAL',    null,                                                     0, 0, 1, 3),
      ('article', 'weight',         '重量',     'INT',        null,                                                     0, 0, 1, 4),
      ('article', 'featured',       '首页推荐', 'BOOL',       null,                                                     0, 0, 1, 5),
      ('article', 'sku',            '货号',     'TEXT',       null,                                                     0, 0, 0, 6),
      ('article', 'specs',          '规格参数', 'JSON',       null,                                                     0, 0, 0, 7),
      ('article', 'gallery',        '图集',     'IMAGES',     null,                                                     0, 0, 0, 8),
      ('article', 'download',       '资料下载', 'FILE',       null,                                                     0, 0, 0, 9),
      ('article', 'relatedProducts','相关产品', 'RELATION',   null,                                                     0, 1, 1, 10),
      ('article', 'readingLevel',   '阅读难度', 'ENUM_MULTI', 'beginner:入门,advanced:进阶',                             0, 0, 1, 11),
      ('article', 'tags',           '标签',     'TAGS',       null,                                                     0, 0, 1, 12),
      ('product', 'brand',          '品牌',     'ENUM',       'acme:Acme,globex:Globex,initech:Initech',                 0, 0, 1, 1),
      ('product', 'region',         '区域',     'ENUM',       'huadong:华东,huanan:华南,huabei:华北',                    0, 0, 1, 2),
      ('product', 'price',          '价格',     'DECIMAL',    null,                                                     0, 0, 1, 3),
      ('product', 'stock',          '库存',     'INT',        null,                                                     0, 0, 1, 4),
      ('product', 'featured',       '推荐位',   'BOOL',       null,                                                     0, 0, 1, 5),
      ('product', 'specs',          '规格参数', 'JSON',       null,                                                     0, 0, 0, 6),
      ('product', 'gallery',        '图集',     'IMAGES',     null,                                                     0, 0, 0, 7),
      ('product', 'download',       '下载资料', 'FILE',       null,                                                     0, 0, 0, 8),
      ('product', 'relatedProducts','相关产品', 'RELATION',   null,                                                     0, 0, 1, 9),
      ('book',    'author',         '作者',     'TEXT',       null,                                                     0, 1, 0, 1),
      ('book',    'bookStatus',     '连载状态', 'ENUM',       'serializing:连载中,finished:已完结',                       0, 0, 1, 2),
      ('book',    'tags',           '标签',     'TAGS',       null,                                                     0, 0, 1, 3),
      ('chapter', 'volume',         '分卷',     'TEXT',       null,                                                     0, 0, 0, 1),
      ('author',  'honor',          '荣誉',     'TEXT',       null,                                                     0, 0, 0, 1),
      ('author',  'bio',            '简介',     'TEXTAREA',   null,                                                     1, 1, 0, 2),
      ('author',  'avatar',         '头像',     'IMAGE',      null,                                                     0, 0, 0, 3),
      ('about',   'subtitle',       '副标题',   'TEXT',       null,                                                     0, 0, 0, 1),
      ('contact', 'subtitle',       '副标题',   'TEXT',       null,                                                     0, 0, 0, 1)
  ) as v(type_code, code, label, field_type, options, raw, searchable, indexed, sort)
 where s.code = 'demo' and s.deleted = 0
   and f.site_id = s.id
   and f.type_code = v.type_code
   and f.code = v.code
   and f.deleted = 0;

-- ---------------------------------------------------------------------------
-- 5. 分类与标签（§2.4）
--
-- cms_category 是复用的旧表（V1__init.sql），多了 site_id（V20261001004746）；
-- 唯一索引是 (site_id, slug) where deleted = 0，所以判存必须带 site_id。
-- 分类树：news 新闻 → tech 科技，另一个根 case 案例。
-- ---------------------------------------------------------------------------
insert into cms_category (site_id, parent_id, name, slug, description, sort, status)
select s.id, 0, v.name, v.slug, v.description, v.sort, 1
  from cms_site s
  cross join (values
      ('新闻', 'news', '公司动态与产品发布', 1),
      ('案例', 'case', '客户落地案例',       2)
  ) as v(name, slug, description, sort)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_category c
                    where c.site_id = s.id and c.slug = v.slug and c.deleted = 0);

insert into cms_category (site_id, parent_id, name, slug, description, sort, status)
select s.id, (select c.id from cms_category c
               where c.site_id = s.id and c.slug = 'news' and c.deleted = 0),
       '科技', 'tech', '技术实践与工具链', 1, 1
  from cms_site s
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_category c
                    where c.site_id = s.id and c.slug = 'tech' and c.deleted = 0);

-- 标签表没有 sort 列（V1__init.sql 的 cms_tag 只有 name / slug），页面上按名称排序
insert into cms_tag (site_id, name, slug)
select s.id, v.name, v.slug
  from cms_site s
  cross join (values
      ('Java',     'java'),
      ('模板引擎',  'template'),
      ('内容管理',  'cms'),
      ('静态化',    'static'),
      ('设计',      'design')
  ) as v(name, slug)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_tag tg
                    where tg.site_id = s.id and tg.slug = v.slug and tg.deleted = 0);

-- ---------------------------------------------------------------------------
-- 6. 内容
--
-- 统一套路：insert ... select from (values ...) 一次性写进去（不写死 id），
-- 后续用 slug 反查 id 再补关联（作者、相关产品、标签、分类、索引行）。
-- ---------------------------------------------------------------------------

-- 6.1 作者（author × 3）：article / book 的 author_id 指向这里
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, cover, status, sort,
                         top, recommend, publish_time, view_count, content_format, content, data,
                         word_count, view_count_day, view_count_week, comment_count,
                         rating_avg, rating_count)
select s.id, 'author', 0, v.slug, v.title, v.summary, v.cover, 'PUBLISHED', v.sort,
       0, 0, v.publish_time::timestamp, v.view_count, 'RICHTEXT', v.content, v.data::jsonb,
       v.word_count, v.view_count / 8, v.view_count / 3, v.view_count / 40,
       v.rating_avg::numeric, v.view_count / 25
  from cms_site s
  cross join (values
      ('lin-jiu',  '林久',   '系统架构师，十年后端与发布链路经验，主持过三次全站静态化改造。',
       '/uploads/demo/avatar-lin-jiu.jpg', 1, '2025-11-02 09:30:00', 4120,
       '<p>林久，系统架构师。关注内容基础设施、构建流水线与静态产物的可运维性。</p>',
       '{"honor":"2024 年度技术贡献奖","bio":"十年后端开发经验，做过三次全站静态化改造，主张把复杂度放回构建期。","avatar":"/uploads/demo/avatar-lin-jiu.jpg"}', 42, 4.80),
      ('chen-mo',  '陈默',   '前端工程师，负责主题模板与设计系统，偏爱零依赖的渲染方案。',
       '/uploads/demo/avatar-chen-mo.jpg', 2, '2025-11-18 14:05:00', 2860,
       '<p>陈默，前端工程师。负责主题模板、设计系统与静态站点的可访问性。</p>',
       '{"honor":"内部设计系统发起人","bio":"写主题模板比写框架更开心，尺子量过每一个行高。","avatar":"/uploads/demo/avatar-chen-mo.jpg"}', 38, 4.60),
      ('su-qing',  '苏晴',   '内容编辑，负责选题与结构化写作，相信好内容需要好的信息架构。',
       '/uploads/demo/avatar-su-qing.jpg', 3, '2025-12-01 10:00:00', 1980,
       '<p>苏晴，内容编辑。负责选题策划、结构化写作与站点的信息架构。</p>',
       '{"honor":"年度优秀编辑","bio":"相信好内容需要好的信息架构，反对把文章写成没有层次的墙。","avatar":"/uploads/demo/avatar-su-qing.jpg"}', 40, 4.90)
  ) as v(slug, title, summary, cover, sort, publish_time, view_count, content, data, word_count, rating_avg)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'author'
                      and c.slug = v.slug and c.deleted = 0);

-- 6.2 书籍（book × 3，TREE 的根：parent_id = 0）
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, cover, status, sort,
                         top, recommend, publish_time, view_count, content_format, content, data,
                         word_count, view_count_day, view_count_week, comment_count,
                         rating_avg, rating_count)
select s.id, 'book', 0, v.slug, v.title, v.summary, v.cover, 'PUBLISHED', v.sort,
       v.top, v.recommend, v.publish_time::timestamp, v.view_count, 'RICHTEXT', v.content, v.data::jsonb,
       v.word_count, v.view_count / 10, v.view_count / 4, v.view_count / 30,
       v.rating_avg::numeric, v.view_count / 40
  from cms_site s
  cross join (values
      ('static-site-notes', '静态站点笔记', '把内容变成文件：从页面计划、增量依赖到产物回收的完整笔记。',
       '/uploads/demo/book-static.jpg', 1, 1, 0, '2025-09-12 08:00:00', 15600,
       '<p>这本书把"内容到文件"的每一步拆开讲：URL 规则、页面计划、分页派生、增量依赖与产物回收。</p><h2>适合谁读</h2><p>已经在用 CMS，又不想让每个页面都过一次后端的人。</p>',
       '{"author":"林久","bookStatus":"serializing","tags":[4]}', 96, 4.70),
      ('template-engine', '模板引擎手记', '从词法扫描到编译期校验，手写一个只认识业务字段的小引擎。',
       '/uploads/demo/book-template.jpg', 2, 0, 1, '2025-10-08 09:20:00', 9800,
       '<p>模板引擎不该是一门口语：五类词法元素、一个 EBNF、编译期把错误拦在发布之前。</p><h2>读法建议</h2><p>先读语法规格，再回到实现看它如何被判死。</p>',
       '{"author":"陈默","bookStatus":"finished","tags":[2,5]}', 74, 4.50),
      ('cms-practice', '内容管理实践', '内容类型、字段定义与索引表：不做动态 DDL 的组织方式。',
       '/uploads/demo/book-cms.jpg', 3, 0, 0, '2025-11-20 13:40:00', 7300,
       '<p>站点自助定义内容类型，字段值统一落进 jsonb 与索引表，表结构因此长期稳定。</p><h2>目录安排</h2><p>类型 → 字段 → 索引 → 查询，顺序即依赖顺序。</p>',
       '{"author":"苏晴","bookStatus":"serializing","tags":[3]}', 68, 4.60)
  ) as v(slug, title, summary, cover, sort, top, recommend, publish_time, view_count, content, data, word_count, rating_avg)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'book'
                      and c.slug = v.slug and c.deleted = 0);

-- 6.3 章节（chapter × 15：每本书 5 章，sort 按 11/13/15/17/19 这类顺序号递增，留出跳号）
-- 正文体例统一为"引言 + 三个 h2 + h3 小节 + 列表"，其中 11 章各插一个
-- <!--cms:page-->（另外 4 章不分页，让"分页/不分页"两种章节页都存在）。
-- 章节类型 paginate_body='content'，因此带分页符的那 11 章会派生出 DPAGE 页。
-- word_count 按"去掉标签后的字符数"当场算，不另填一列（§5.2.5 的口径）。
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, cover, status, sort,
                         top, recommend, publish_time, view_count, content_format, content, data,
                         word_count, view_count_day, view_count_week, comment_count,
                         rating_avg, rating_count)
select s.id, 'chapter', b.id, v.slug, v.title, v.summary, null, 'PUBLISHED', v.sort,
       0, 0, v.publish_time::timestamp, v.view_count, 'RICHTEXT', body.content,
       jsonb_build_object('volume', v.volume),
       length(regexp_replace(body.content, '<[^>]*>', '', 'g')),
       v.view_count / 10, v.view_count / 4, v.view_count / 35,
       v.rating_avg::numeric, v.view_count / 50
  from cms_site s
  join cms_content b on b.site_id = s.id and b.type_code = 'book' and b.deleted = 0
  cross join (values
      ('static-site-notes', 'chapter-01', 11, 1, '为什么要静态化', '从一次真实的访问延迟说起：文件从磁盘直达浏览器，中间没有一次后端参与。',
       '2025-09-12 08:10:00', 3200, '卷一', 4.40),
      ('static-site-notes', 'chapter-03', 13, 2, '页面计划', '一个页面从内容变成文件之前，先要在内存里存在一次。',
       '2025-09-15 09:00:00', 2650, '卷一', 4.50),
      ('static-site-notes', 'chapter-05', 15, 3, 'URL 与产物路径', 'URL 是给人和搜索引擎看的，产物路径是给文件系统看的，两者靠一套规则绑定。',
       '2025-09-20 10:30:00', 2100, '卷一', 4.60),
      ('static-site-notes', 'chapter-08', 17, 4, '增量依赖', '改一条内容不该重发全站，前提是你知道它影响了哪些页面。',
       '2025-10-02 11:20:00', 2400, '卷二', 4.70),
      ('static-site-notes', 'chapter-12', 19, 5, '产物回收', '删掉的内容对应的文件谁来删：GC 只认本次计划内的产物清单。',
       '2025-10-11 15:40:00', 1750, '卷二', 4.30),
      ('template-engine',   'chapter-01', 11, 1, '五种词法元素', '模板语法一旦膨胀，模板作者就要开始学一门语言，这是要避免的。',
       '2025-10-08 09:30:00', 2200, '卷一', 4.50),
      ('template-engine',   'chapter-02', 14, 2, 'EBNF 与参数文法', '把语法写成 EBNF，争议就从"我觉得"变成"这条产生式是否允许"。',
       '2025-10-12 10:10:00', 1900, '卷一', 4.40),
      ('template-engine',   'chapter-04', 16, 3, '扫描与配对', '嵌套标签不匹配就报错，不做容错：静默容错会把错误推到产物里。',
       '2025-10-18 14:30:00', 1650, '卷一', 4.60),
      ('template-engine',   'chapter-06', 18, 4, '作用域栈', '字段解析的顺序写死，模板作者就不需要记住谁的优先级更高。',
       '2025-10-25 09:50:00', 1520, '卷二', 4.30),
      ('template-engine',   'chapter-09', 19, 5, '编译期校验', '自研引擎最大的红利是能把错误拦在发布之前，而不是等访客先看到。',
       '2025-11-01 16:00:00', 1800, '卷二', 4.80),
      ('cms-practice',      'chapter-02', 13, 1, '内容类型与页面类型', '两者正交：类型决定字段与模板，页面类型决定产物与 URL。',
       '2025-11-20 13:50:00', 1480, '卷一', 4.50),
      ('cms-practice',      'chapter-03', 15, 2, '字段定义与索引表', '不做动态 DDL 的代价是写放大，换来的是表结构长期稳定。',
       '2025-11-25 10:20:00', 1320, '卷一', 4.40),
      ('cms-practice',      'chapter-05', 17, 3, '正文分页与目录', '长正文拆成多页要占位符、模板与 canonical 三方同时配合。',
       '2025-12-02 11:40:00', 1180, '卷一', 4.20),
      ('cms-practice',      'chapter-07', 20, 4, '多语言即多站点', '语言不是内容的一个字段，而是站点的一个维度。',
       '2025-12-10 09:10:00', 1050, '卷二', 4.60),
      ('cms-practice',      'chapter-09', 22, 5, '用菜单表达当前页', '当前项高亮由引擎比对 URL 得出，模板里一行比较都不用写。',
       '2025-12-18 15:30:00', 1260, '卷二', 4.70)
  ) as v(book_slug, slug, sort, seq, title, summary, publish_time, view_count, volume, rating_avg)
  cross join lateral (
      values (case when v.sort in (11, 13, 15, 17, 21, 14, 19) then '<p>正文分页这件事，只有在内容足够长的时候才值得做：一次渲染出的 HTML 越大，首屏越慢，而读者往往只关心前两屏。</p><!--cms:page--><p>本章接着讲拆分之后 URL、目录与站内链接要一起调整的地方，任何一处落下都会在两个页面之间留下断链。</p>' else '' end)
  ) as marker(pager)
  cross join lateral (
      values (
        '<p>' || v.summary || '</p>'
        || '<h2>这一章解决什么</h2><p>把问题限定在一件事上：' || v.title || '。先把边界划清楚，再谈实现，否则讨论会滑向"顺便也重构一下"。</p>'
        || '<h3>先看现象</h3><p>现象通常出现在两个地方：访问变慢，或者发布之后页面数量对不上。两者都不是编辑器的问题。</p>'
        || '<h3>再看约束</h3><p>约束来自三处：内容模型的表达能力、模板能拿到的字段、以及产物路径的边界规则。任何一处放开，另一处就要收回来。</p>'
        || marker.pager
        || '<h2>怎么做</h2><p>做法归纳成四步：<strong>定位影响面</strong>、<em>收敛改动点</em>、写一条可复现的验证、把验证留在仓库里。</p>'
        || '<ul><li>定位影响面：从内容 id 出发列出全部产物路径；</li><li>收敛改动点：一次只动一个维度的规则；</li><li>可复现验证：命令与期望输出都写下来；</li><li>留在仓库：下次改动不用重新推导。</li></ul>'
        || '<h3>容易踩的坑</h3><p>坑不在实现里，在命名里：同一个概念在两个模块用了两个名字，改起来就会漏掉一半。</p>'
        || '<h2>小结</h2><p>' || v.title || ' 的答案通常是"把决定提前"：能在编译期判断的，不放到发布期；能在发布期判断的，不放到请求期。'
        || '<blockquote><p>静态站点不是没有动态能力，而是把动态的时机提前到了构建时。</p></blockquote></p>'
      )
  ) as body(content)
 where s.code = 'demo' and s.deleted = 0 and b.slug = v.book_slug
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'chapter'
                      and c.parent_id = b.id and c.slug = v.slug and c.deleted = 0);

-- 6.4 文章（article × 14）
-- publish_time 从 2026-01 铺到 2026-03，月归档、年归档与列表第二页都有数据；
-- view_count 与 publish_time 不同序（榜单页与列表页的顺序因此不同）。
-- data 里把 article 的 12 个自定义字段全部填满（readingLevel 给 1–2 个取值，
-- 第 7 节的索引行按 unnest 展开）。
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, cover, status, sort,
                         top, recommend, publish_time, view_count, content_format, content, data,
                         word_count, view_count_day, view_count_week, comment_count,
                         rating_avg, rating_count)
select s.id, 'article', 0, v.slug, v.title, v.summary, v.cover, 'PUBLISHED', v.sort,
       v.top, v.recommend, v.publish_time::timestamp, v.view_count, 'RICHTEXT',
       v.content, v.data::jsonb,
       v.word_count, v.view_count / 5, v.view_count / 2, v.view_count / 45,
       v.rating_avg::numeric, v.view_count / 60
  from cms_site s
  cross join (values
      ('static-first-byte', '静态站点的第一次访问为什么更快',
       '同一台机器、同一份内容，静态产物省掉的是整条请求链路，而不是某一次查询。',
       '/uploads/demo/cover-01.jpg', 1, 1918, 0, 10, '2026-01-06 09:20:00', 4820, 4.60,
       '<p>把同一篇文章分别放在动态站与静态站上，第一次访问的差异并不来自数据库快不快，而来自"要经过多少环节才能把第一个字节交出去"。</p>
<h2>请求链路的长度</h2>
<p>动态请求要经过路由、鉴权、查询、模板渲染四段，任何一段排队，首字节都会被推后。静态请求只有"打开文件、读出来"两段。</p>
<h3>排队从哪里来</h3>
<p>来自并发：单条查询只要十毫秒，一百个请求同时到达就会排在同一个连接池后面。</p>
<h2>缓存解决不了的部分</h2>
<p>页面级缓存能覆盖大部分情况，但缓存失效与回源仍然要回到同一条链路上，而且冷启动时第一个访客承担全部代价。</p>
<h3>静态产物把代价前移</h3>
<p>内容变更时重新生成一次文件，之后的每一次访问都不再触发渲染。</p>
<h2>结论</h2>
<p>先量化，再决定：把首字节时间按链路分段测量，静态化能省掉的那几段会自己浮现出来。</p>',
       '{"brand":"Acme","region":"huadong","price":1299.00,"weight":1200,"featured":1,"sku":"AC-NET-001","readingLevel":["beginner"],"specs":[{"key":"适用规模","value":"日均 10 万 PV 以内"},{"key":"部署方式","value":"nginx 直接托管"}],"gallery":[{"url":"/uploads/demo/cover-01.jpg","alt":"机房机柜","width":1280,"height":720},{"url":"/uploads/demo/detail-01.jpg","alt":"访问延迟曲线","width":1280,"height":720}],"download":{"url":"/uploads/demo/static-first-byte.pdf","name":"首字节测量表.pdf","size":182400,"mime":"application/pdf"}}', 480),
      ('tag-language-for-pages', '用标签语言描述页面结构',
       '只有五类词法元素的模板语言，为什么反而比"能写代码的模板"更适合交给编辑。',
       '/uploads/demo/cover-02.jpg', 2, 402, 0, 0, '2026-01-11 10:05:00', 3160, 4.40,
       '<p>模板语法每多一条能力，模板作者的认知负担就多一层。把能力收在标签参数里，模板就退化成一份可读的结构描述。</p>
<h2>五类词法元素</h2>
<p>文本、标签、字段、注释、字面量。五种之外不再增加，插值一律通过字段标签完成。</p>
<h3>参数只写字面量</h3>
<p>参数不做字段插值，因此一个模板的查询目标在编译期就能确定，缓存键也就能算出来。</p>
<h2>为什么不做表达式</h2>
<p>一旦允许在模板里算，业务规则就会分裂到模板与后端两处，两边都不知道对方改了什么。</p>
<h2>结论</h2>
<p>表达能力放在字段定义里，模板只负责组装结构，这样换主题才不用重写业务。</p>',
       '{"brand":"Globex","region":"huabei","price":899.00,"weight":800,"featured":0,"sku":"GB-TPL-002","readingLevel":["beginner","advanced"],"specs":[{"key":"词法元素","value":"5 类"},{"key":"缓存","value":"按模板版本失效"}],"gallery":[{"url":"/uploads/demo/cover-02.jpg","alt":"模板语法卡片","width":1280,"height":720}],"download":{"url":"/uploads/demo/tag-language.pdf","name":"标签语言速查表.pdf","size":96400,"mime":"application/pdf"}}', 420),
      ('page-plan-and-derived-pages', '从内容到文件：页面计划是怎么生成的',
       '同一个模板、同一份上下文，只改页码与输出路径，就得到第 2..N 页。',
       '/uploads/demo/cover-03.jpg', 3, 1105, 1, 20, '2026-01-19 14:40:00', 5240, 4.80,
       '<p>分页不是渲染时"顺手多渲染几次"，而是计划期就已经算清楚的一件事。</p>
<h2>计划期先固化四件事</h2>
<p>页面类型、上下文来源、输出路径、分页号。四件事定了，渲染就只是执行。</p>
<h3>分页主体只有一个</h3>
<p>一个模板至多一个可分页的列表，否则第 2 页该取哪一段数据无法判定。</p>
<h2>派生页与主页面同模板</h2>
<p>主页面渲染一次，派生页只覆盖页号重渲染；模板不需要为第 2 页写第二份。</p>
<h2>冲突在计划期就报</h2>
<p>两个来源映射到同一路径时直接失败，宁可不发布，也不发布一个被覆盖的页面。</p>',
       '{"brand":"Acme","region":"huanan","price":1580.00,"weight":1500,"featured":1,"sku":"AC-PLN-003","readingLevel":["advanced"],"specs":[{"key":"页面类型","value":"11 种"},{"key":"派生方式","value":"计划期固化"}],"gallery":[{"url":"/uploads/demo/cover-03.jpg","alt":"页面计划草图","width":1280,"height":720}],"download":{"url":"/uploads/demo/page-plan.pdf","name":"页面计划说明.pdf","size":240800,"mime":"application/pdf"}}', 460),
      ('url-pattern-and-output-path', 'URL 规则与产物路径的映射',
       'URL 是给人和搜索引擎看的，产物路径是给文件系统看的，一条规则把两者钉在一起。',
       '/uploads/demo/cover-04.jpg', 4, 860, 0, 0, '2026-01-27 09:15:00', 2580, 4.30,
       '<p>URL 规则里能出现哪些占位符是一份封闭清单，清单外的一律在编译期报错。</p>
<h2>占位符白名单</h2>
<p>slug、id、父 slug、排序号、分类路径、类型 code、年月日、标签 slug、筛选路径、页码。清单之外没有例外。</p>
<h3>第 1 页不含页码</h3>
<p>删除页码占位符时连同紧邻的一个分隔符一起删，规则是逐字符的，不是"截断到某个位置"。</p>
<h2>路径映射写死</h2>
<p>以斜杠结尾的 URL 只映射到 index.html，不再生成同名文件；尾斜杠唯一，不需要服务器做二次猜测。</p>
<h2>结论</h2>
<p>把规则集中在一处实现，模板与文档都不许自己拼路径。</p>',
       '{"brand":"Initech","region":"huadong","price":660.00,"weight":500,"featured":0,"sku":"IN-URL-004","readingLevel":["beginner"],"specs":[{"key":"占位符","value":"11 个"},{"key":"大小写","value":"一律小写"}],"gallery":[{"url":"/uploads/demo/cover-04.jpg","alt":"URL 映射示意","width":1280,"height":720}],"download":{"url":"/uploads/demo/url-pattern.pdf","name":"URL 规则白名单.pdf","size":74200,"mime":"application/pdf"}}', 400),
      ('incremental-dependency', '改一条内容，重发几个页面',
       '增量发布的前提不是"记得清了缓存"，而是手里有一张反向索引。',
       '/uploads/demo/cover-05.jpg', 5, 1234, 0, 0, '2026-02-03 11:00:00', 4390, 4.70,
       '<p>全量重建在几百篇内容时还能忍，上千篇之后就会变成发布窗口的主要成本。</p>
<h2>先有依赖，再谈增量</h2>
<p>输入依赖清单记录"这个页面读了哪些内容、哪些模板、哪些选项"，反向索引则回答"这条内容变了，谁需要重发"。</p>
<h3>模板也算输入</h3>
<p>改一行公共头部会让所有引用它的页面失效，依赖清单里必须包含展开后的模板字节。</p>
<h2>依赖算不准时的退路</h2>
<p>算不准就向上取整：宁可多发几个页面，也不要漏发一个。漏发表现为"内容改了线上没变"，最难排查。</p>
<h2>结论</h2>
<p>增量不是优化技巧，是正确性问题；先保证不漏，再谈少发。</p>',
       '{"brand":"Globex","region":"huanan","price":2140.00,"weight":2100,"featured":0,"sku":"GB-INC-005","readingLevel":["advanced"],"specs":[{"key":"依赖粒度","value":"内容 + 模板 + 选项"},{"key":"失效方式","value":"反向索引"}],"gallery":[{"url":"/uploads/demo/cover-05.jpg","alt":"依赖图谱","width":1280,"height":720}],"download":{"url":"/uploads/demo/incremental.pdf","name":"增量发布依赖表.pdf","size":156000,"mime":"application/pdf"}}', 440),
      ('content-type-metadata', '内容类型不是数据库表',
       '站点自助定义类型，字段值统一进 jsonb 与索引表，表结构因此可以三年不动。',
       '/uploads/demo/cover-06.jpg', 6, 1502, 0, 0, '2026-02-09 15:30:00', 3670, 4.50,
       '<p>"每个类型建一张表"是最直觉的做法，也是最难维护的做法：字段一改就要 DDL，两个环境的结构迟早不一致。</p>
<h2>元数据化的代价</h2>
<p>值不再有列级约束，类型校验落到应用层；换来的是加字段不需要停机、发布不需要改表。</p>
<h3>索引表承担筛选</h3>
<p>声明了可筛选的字段，每个取值写一行索引；查询只读索引表，计划因此稳定。</p>
<h2>写放大会到多少</h2>
<p>一条内容多出十几行索引行，相对一次发布要生成的文件数，这个量级可以接受。</p>
<h2>结论</h2>
<p>代价如实记录，收益是表结构稳定——而表结构的稳定是长期运维里最贵的东西。</p>',
       '{"brand":"Acme","region":"huabei","price":1780.00,"weight":1600,"featured":1,"sku":"AC-TYP-006","readingLevel":["beginner","advanced"],"specs":[{"key":"字段类型","value":"19 种"},{"key":"索引方式","value":"每取值一行"}],"gallery":[{"url":"/uploads/demo/cover-06.jpg","alt":"类型与字段关系图","width":1280,"height":720}],"download":{"url":"/uploads/demo/content-type.pdf","name":"内容类型设计说明.pdf","size":198400,"mime":"application/pdf"}}', 450),
      ('render-scope-and-field', '渲染作用域与字段解析',
       '字段在哪个作用域里取值，顺序写死之后，模板作者就不用记住优先级。',
       '/uploads/demo/cover-07.jpg', 7, 715, 0, 0, '2026-02-16 10:45:00', 2210, 4.20,
       '<p>作用域栈的规则只有一句：从最内层往外找，找到即止，找不到就报错。</p>
<h2>三层作用域</h2>
<p>循环项、页面上下文、站点上下文。嵌套循环时以内层为准，需要外层时用显式前缀。</p>
<h3>找不到即报错</h3>
<p>静默输出空串会把错误推到线上，编译期把字段名与实际定义对照一次，代价极小。</p>
<h2>派生字段的位置</h2>
<p>URL、分类、标签、作者、正文计量这些都是引擎算出来的，模板直接用，不做条件判断。</p>
<h2>结论</h2>
<p>把"怎么算"留在引擎里，模板里只剩"放在哪"。</p>',
       '{"brand":"Initech","region":"huanan","price":520.00,"weight":400,"featured":0,"sku":"IN-SCP-007","readingLevel":["beginner"],"specs":[{"key":"作用域层数","value":"3 层"},{"key":"缺字段策略","value":"编译期报错"}],"gallery":[{"url":"/uploads/demo/cover-07.jpg","alt":"作用域栈示意","width":1280,"height":720}],"download":{"url":"/uploads/demo/render-scope.pdf","name":"作用域规则速查.pdf","size":64200,"mime":"application/pdf"}}', 380),
      ('search-index-without-tokenizer', '没有分词器也能搜索',
       '静态 JSON 分片 + 前端子串匹配：中文按字包含天然正确，英文按词前缀。',
       '/uploads/demo/cover-08.jpg', 8, 904, 0, 1, '2026-02-22 13:10:00', 3980, 4.60,
       '<p>引入分词器意味着多一个依赖、多一份词典、多一种"不同环境结果不同"的可能。</p>
<h2>产物形态</h2>
<p>索引按固定条数切片，每片一个 JSON 数组；清单文件给出片数与版本号，前端按需拉取。</p>
<h3>字段用短名</h3>
<p>索引体积是这条链路上最贵的资源，字段名从 title 缩到 t 省下的是实打实的带宽。</p>
<h2>匹配策略</h2>
<p>中文按字包含匹配天然正确，英文按词前缀匹配；搜不到同义词与拼音，这一条如实写在搜索页上。</p>
<h2>结论</h2>
<p>五万条以内纯前端足够；再往上切接口模式，索引产物仍然生成，作为降级路径。</p>',
       '{"brand":"Globex","region":"huadong","price":1360.00,"weight":1100,"featured":1,"sku":"GB-SRH-008","readingLevel":["advanced"],"specs":[{"key":"分片大小","value":"2000 条"},{"key":"截断长度","value":"1000 字"}],"gallery":[{"url":"/uploads/demo/cover-08.jpg","alt":"搜索索引分片","width":1280,"height":720}],"download":{"url":"/uploads/demo/search-index.pdf","name":"搜索索引产物说明.pdf","size":121600,"mime":"application/pdf"}}', 430),
      ('toc-and-body-pagination', '正文分页与目录的配合',
       '分页符是正文里唯一被保留的注释，目录锚点必须在清洗前生成。',
       '/uploads/demo/cover-09.jpg', 9, 640, 0, 0, '2026-02-27 16:20:00', 2050, 4.40,
       '<p>长正文拆页之后，页面之间要有明确的导航，读者才不会以为文章到这里就结束了。</p>
<h2>分页符的位置</h2>
<p>分页符插在段落之间，插入点由编辑决定；引擎只在它出现的地方断页，不做自动均衡。</p>
<h3>锚点必须稳定</h3>
<p>目录锚点用引擎生成的编号而不是标题文本，中文标题做锚点会产出需要编码的乱码链接。</p>
<h2>与 canonical 的关系</h2>
<p>每一页的 canonical 指向自己，第 2 页之后是否收录由站点选项决定。</p>
<h2>结论</h2>
<p>正文分页是内容问题，不是模板问题：结构清楚的文章，拆页之后每一页都能独立阅读。</p>',
       '{"brand":"Acme","region":"huadong","price":1490.00,"weight":1300,"featured":0,"sku":"AC-TOC-009","readingLevel":["beginner","advanced"],"specs":[{"key":"目录层级","value":"h2,h3"},{"key":"分页符","value":"正文唯一保留注释"}],"gallery":[{"url":"/uploads/demo/cover-09.jpg","alt":"正文分页示意","width":1280,"height":720}],"download":{"url":"/uploads/demo/toc-paging.pdf","name":"正文分页与目录.pdf","size":88400,"mime":"application/pdf"}}', 410),
      ('facet-pages-are-configured', '筛选落地页是配置出来的',
       '不做全排列：手工声明组合，只为真的有内容的取值出页。',
       '/uploads/demo/cover-10.jpg', 10, 977, 0, 0, '2026-03-03 09:40:00', 2870, 4.50,
       '<p>两个字段各十个取值，全排列就是一百个页面，其中九十个是空壳。</p>
<h2>只声明要的组合</h2>
<p>组合写在站点选项里，一条组合一个落地页；取值没有内容时不生成，避免空页面进收录。</p>
<h3>页面上限与取值上限</h3>
<p>两个上限分别兜住"组合爆炸"与"取值太散"，超限直接报错而不是悄悄少生成。</p>
<h2>筛选页要不要收录</h2>
<p>默认不收录，由站点选项统一控制；行业站想让价格对比页被搜到，再逐个放开。</p>
<h2>结论</h2>
<p>筛选页是运营配置，不是引擎的自动行为——这一点决定了它不会失控。</p>',
       '{"brand":"Globex","region":"huabei","price":1120.00,"weight":950,"featured":1,"sku":"GB-FCT-010","readingLevel":["beginner"],"specs":[{"key":"组合上限","value":"500 页"},{"key":"取值上限","value":"50 个"}],"gallery":[{"url":"/uploads/demo/cover-10.jpg","alt":"筛选组合矩阵","width":1280,"height":720}],"download":{"url":"/uploads/demo/facet-pages.pdf","name":"筛选页配置说明.pdf","size":103200,"mime":"application/pdf"}}', 420),
      ('menu-as-data', '把导航做成数据',
       '栏目、单页、外链混排在一个迭代里，当前项高亮由引擎比对得出。',
       '/uploads/demo/cover-11.jpg', 11, 733, 0, 0, '2026-03-08 11:25:00', 1960, 4.30,
       '<p>导航是数据而不是模板里的硬编码，改导航就不用改模板、不用重新编译前端。</p>
<h2>菜单项的指向</h2>
<p>可以指向分类、内容、类型列表页、标签、归档、作者或外链；指向什么就写什么，模板不关心差异。</p>
<h3>当前页高亮</h3>
<p>引擎比对菜单项解析出的 URL 与当前页面 URL 得出结论，模板里不写比较。</p>
<h2>代价</h2>
<p>菜单属于数据，改菜单等于全站导航失效，因此走一次增量发布；换来的是运营能自己改。</p>
<h2>结论</h2>
<p>能交给数据的就不放进模板，这是静态站长期可维护的前提。</p>',
       '{"brand":"Initech","region":"huadong","price":780.00,"weight":600,"featured":0,"sku":"IN-MNU-011","readingLevel":["beginner","advanced"],"specs":[{"key":"菜单层级","value":"二级下拉"},{"key":"指向类型","value":"8 种"}],"gallery":[{"url":"/uploads/demo/cover-11.jpg","alt":"导航菜单结构","width":1280,"height":720}],"download":{"url":"/uploads/demo/menu-as-data.pdf","name":"菜单数据模型.pdf","size":71200,"mime":"application/pdf"}}', 400),
      ('media-and-derived-sizes', '媒体派生尺寸怎么算',
       '原图只存一份，三档派生尺寸在产物阶段生成，模板按用途取用。',
       '/uploads/demo/cover-12.jpg', 12, 654, 0, 0, '2026-03-14 14:55:00', 1740, 4.10,
       '<p>列表页要缩略图，详情页要原图，正文里还要一张中等宽度——同一张图三份尺寸。</p>
<h2>派生档位</h2>
<p>长边三档，由站点选项声明；改档位要重建派生文件，因此这项改动会写进发布报告。</p>
<h3>元数据同批入库</h3>
<p>宽高在入库时读出，模板才能提前给 img 写好尺寸，避免布局抖动。</p>
<h2>URL 前缀可配</h2>
<p>本地托管走站内路径，接 CDN 时只改一个选项，内容与模板都不用动。</p>
<h2>结论</h2>
<p>媒体是静态站里最容易被忽略的部分，也是体积的主要来源；先把档位定死，再谈优化。</p>',
       '{"brand":"Acme","region":"huanan","price":1980.00,"weight":1700,"featured":1,"sku":"AC-MED-012","readingLevel":["advanced"],"specs":[{"key":"派生尺寸","value":"320,768,1280"},{"key":"状态","value":"PENDING/DONE/FAILED"}],"gallery":[{"url":"/uploads/demo/cover-12.jpg","alt":"派生尺寸对比","width":1280,"height":720}],"download":{"url":"/uploads/demo/media-derive.pdf","name":"媒体派生尺寸说明.pdf","size":143600,"mime":"application/pdf"}}', 430),
      ('gallery-and-album', '图集与详情页的排版',
       '同一组图片在列表页只出首图，在详情页全部展开，靠的是字段类型而不是模板判断。',
       '/uploads/demo/cover-13.jpg', 13, 1054, 0, 0, '2026-03-21 10:30:00', 2230, 4.40,
       '<p>图集字段存的是有序数组，首图与全部图片是同一个字段的两种取法。</p>
<h2>数组顺序就是录入顺序</h2>
<p>不排序、不打散，编辑拖一次顺序结果就变了，模板不需要额外的排序参数。</p>
<h3>无障碍文本</h3>
<p>每张图带 alt，为空时回退到当前内容标题；这是模板一行都不该省的地方。</p>
<h2>懒加载</h2>
<p>详情页图集默认懒加载，首屏只加载第一张，剩下的交给浏览器。</p>
<h2>结论</h2>
<p>把排版差异交给字段与模板，不要交给运行时的条件判断。</p>',
       '{"brand":"Globex","region":"huanan","price":2450.00,"weight":1900,"featured":0,"sku":"GB-GAL-013","readingLevel":["beginner","advanced"],"specs":[{"key":"图集张数","value":"不限"},{"key":"首图","value":"列表页唯一输出"}],"gallery":[{"url":"/uploads/demo/cover-13.jpg","alt":"图集排版","width":1280,"height":720}],"download":{"url":"/uploads/demo/gallery.pdf","name":"图集字段说明.pdf","size":92800,"mime":"application/pdf"}}', 410),
      ('release-and-rollback', '发布批次与回滚',
       '批次的产物先写预发布目录，人工确认后再提升；出问题就回滚到上一批。',
       '/uploads/demo/cover-14.jpg', 14, 481, 0, 0, '2026-03-28 16:05:00', 1520, 4.20,
       '<p>静态发布的风险不在"生成失败"，而在"生成成功但生成错了"。</p>
<h2>批次是一等公民</h2>
<p>每次发布是一个批次，产物落到该批次的目录下；保留最近几批，回滚就是切一次软链。</p>
<h3>预发布</h3>
<p>开启预发布后产物先写预览目录，人工点一遍确认没有破版再提升，正式目录全程不动。</p>
<h2>产物清单</h2>
<p>清单记录本次生成了哪些文件与它们的哈希，回收与回滚都以它为准，不靠目录扫描。</p>
<h2>结论</h2>
<p>能一键回滚的发布流程，才敢让运营自己点发布按钮。</p>',
       '{"brand":"Initech","region":"huabei","price":1080.00,"weight":880,"featured":0,"sku":"IN-REL-014","readingLevel":["advanced"],"specs":[{"key":"保留批次","value":"3 个"},{"key":"预发布","value":"可选"}],"gallery":[{"url":"/uploads/demo/cover-14.jpg","alt":"发布批次目录","width":1280,"height":720}],"download":{"url":"/uploads/demo/release.pdf","name":"发布与回滚手册.pdf","size":167400,"mime":"application/pdf"}}', 420)
  ) as v(slug, title, summary, cover, sort, view_count, top, recommend, publish_time, word_count, rating_avg, content, data)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'article'
                      and c.slug = v.slug and c.deleted = 0);

-- author_id / author_name：3 位作者轮转分配（作者先插入，这里才拿得到 id）
update cms_content c
   set author_id     = a.id,
       author_name   = a.title,
       update_time   = c.publish_time + interval '2 hours'
  from cms_site s
  join cms_content a on a.site_id = s.id and a.type_code = 'author' and a.deleted = 0
 where s.code = 'demo' and s.deleted = 0
   and c.site_id = s.id
   and c.type_code = 'article'
   and c.deleted = 0
   and a.slug = (case (c.id % 3) when 0 then 'su-qing' when 1 then 'lin-jiu' else 'chen-mo' end);

-- 书籍的 author_id 同样指向作者内容项（书详情的"作者"栏因此可点）
update cms_content c
   set author_id   = a.id,
       author_name = a.title,
       update_time = c.publish_time + interval '3 hours'
  from cms_site s
  join cms_content a on a.site_id = s.id and a.type_code = 'author' and a.deleted = 0
 where s.code = 'demo' and s.deleted = 0
   and c.site_id = s.id
   and c.type_code = 'book'
   and c.deleted = 0
   and a.slug = c.data ->> 'author'
   and a.slug is not null
   and c.data ->> 'author' is not null;

-- 书籍标签：data.tags 是标签 id 数组，按标签 slug 反查真实 id 写回
update cms_content c
   set data = jsonb_set(coalesce(c.data, '{}'::jsonb), '{tags}',
                        coalesce((select jsonb_agg(t.id order by t.id)
                                    from cms_tag t
                                   where t.site_id = c.site_id and t.deleted = 0
                                     and t.slug in ('static', 'template', 'cms', 'design')), '[]'::jsonb)),
       update_time = c.publish_time + interval '5 hours'
  from cms_site s
 where s.code = 'demo' and s.deleted = 0
   and c.site_id = s.id and c.type_code = 'book' and c.deleted = 0;

-- 6.5 产品（product × 9）
-- 三家品牌各 3 件；区域按 华东 / 华南 / 华北 铺开，其中 acme-gateway-x1 / acme-storage-n2
-- 是 brand='acme' 且 region='huadong'——站点选项 facets.combos 声明的正是这个组合，
-- 没有它生成的筛选页会是空页。download 按 FILE 字段的结构给全四个键。
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, cover, status, sort,
                         top, recommend, publish_time, view_count, content_format, content, data,
                         word_count, view_count_day, view_count_week, comment_count,
                         rating_avg, rating_count)
select s.id, 'product', 0, v.slug, v.title, v.summary, v.cover, 'PUBLISHED', v.sort,
       v.top, v.recommend, v.publish_time::timestamp, v.view_count, 'RICHTEXT', v.content, v.data::jsonb,
       v.word_count, v.view_count / 6, v.view_count / 3, v.view_count / 50,
       v.rating_avg::numeric, v.view_count / 70
  from cms_site s
  cross join (values
      ('acme-gateway-x1', 'Acme 边缘网关 X1',
       '面向区域机房的小型边缘网关：两个万兆口，把静态产物推到离访客最近的节点。',
       'Acme', 'huadong', 6980.00, 128, 1, 1,
       '/uploads/demo/product-01.jpg', 1, '2025-12-08 09:00:00', 5600, 4.70,
       '<p>面向区域机房的小型边缘网关，一个机位放下，两个万兆口，支持站点产物的本地缓存与灰度放量。</p><h2>典型场景</h2><p>把静态产物推到离访客最近的节点，回源只发生在内容变更之后。</p><ul><li>双万兆上行，单机 20 万并发连接</li><li>产物目录只读挂载，回滚一次切换</li><li>内置健康检查与访问日志轮转</li></ul>',
       '{"brand":"acme","region":"huadong","price":6980.00,"stock":128,"featured":1,"specs":[{"key":"网口","value":"2×10GE + 4×GE"},{"key":"整机功耗","value":"45W"},{"key":"机架尺寸","value":"1U"},{"key":"质保","value":"三年"}],"gallery":[{"url":"/uploads/demo/product-01.jpg","alt":"Acme X1 网关正面","width":1280,"height":720}],"download":{"url":"/uploads/demo/acme-gateway-x1.pdf","name":"X1 网关规格书.pdf","size":385600,"mime":"application/pdf"}}', 320),
      ('acme-storage-n2', 'Acme 存储节点 N2',
       '为产物目录与媒体原图准备的存储节点，单盘热插拔，容量从 8TB 起配。',
       'Acme', 'huadong', 12800.00, 46, 0, 0,
       '/uploads/demo/product-02.jpg', 2, '2026-01-14 10:20:00', 3140, 4.50,
       '<p>为产物目录准备的存储节点，单盘可热插拔，容量从 8TB 起配，适合放历史版本与媒体原图。</p><h2>为什么单列一个存储节点</h2><p>媒体原图与多档派生尺寸加起来通常是内容库的数十倍，混在发布节点上会让扩容变成一次停机。</p>',
       '{"brand":"acme","region":"huadong","price":12800.00,"stock":46,"featured":0,"specs":[{"key":"盘位","value":"12 × 3.5 英寸"},{"key":"容量起点","value":"8TB"},{"key":"冗余","value":"RAID 6"},{"key":"质保","value":"三年"}],"gallery":[{"url":"/uploads/demo/product-02.jpg","alt":"Acme N2 存储节点","width":1280,"height":720}],"download":{"url":"/uploads/demo/acme-storage-n2.pdf","name":"N2 存储节点规格书.pdf","size":412800,"mime":"application/pdf"}}', 300),
      ('acme-cdn-edge', 'Acme 边缘加速包',
       '给已有服务器加一层缓存：命中直接返回，未命中回源一次并落盘。',
       'Acme', 'huanan', 3680.00, 220, 0, 1,
       '/uploads/demo/product-03.jpg', 3, '2026-02-06 11:40:00', 2760, 4.40,
       '<p>给已有服务器加一层边缘加速：命中直接返回，未命中回源一次并落盘，适合预算有限又想先见效的场景。</p><h2>上线顺序</h2><p>先在测试域名上跑一周，比对回源率与命中率，再切正式域名。</p>',
       '{"brand":"acme","region":"huanan","price":3680.00,"stock":220,"featured":0,"specs":[{"key":"缓存淘汰","value":"LRU + 手动预热"},{"key":"回源并发","value":"32"},{"key":"日志","value":"按天轮转"},{"key":"质保","value":"一年"}],"gallery":[{"url":"/uploads/demo/product-03.jpg","alt":"边缘加速拓扑","width":1280,"height":720}],"download":{"url":"/uploads/demo/acme-cdn-edge.pdf","name":"边缘加速包说明.pdf","size":256400,"mime":"application/pdf"}}', 290),
      ('globex-builder-b5', 'Globex 构建机 B5',
       '专为批量渲染准备的构建机：多核并行加本地产物盘，全量发布一万页约七分钟。',
       'Globex', 'huabei', 15600.00, 32, 1, 0,
       '/uploads/demo/product-04.jpg', 4, '2025-12-19 14:00:00', 4180, 4.60,
       '<p>专为批量渲染准备的构建机：多核并行 + 本地产物盘，全量发布一万页的实测耗时约七分钟。</p><h2>并行度怎么定</h2><p>默认取 CPU 核数与 4 的较小值，再按每页超时与失败率回头调。</p>',
       '{"brand":"globex","region":"huabei","price":15600.00,"stock":32,"featured":1,"specs":[{"key":"处理器","value":"16 核 32 线程"},{"key":"内存","value":"64GB"},{"key":"产物盘","value":"2TB NVMe"},{"key":"质保","value":"三年"}],"gallery":[{"url":"/uploads/demo/product-04.jpg","alt":"Globex B5 构建机","width":1280,"height":720}],"download":{"url":"/uploads/demo/globex-builder-b5.pdf","name":"B5 构建机规格书.pdf","size":368200,"mime":"application/pdf"}}', 310),
      ('globex-monitor-m3', 'Globex 监控探针 M3',
       '每分钟回访一批关键页面，产物被误删或被覆盖时先于访客发现。',
       'Globex', 'huadong', 2480.00, 310, 0, 0,
       '/uploads/demo/product-05.jpg', 5, '2026-01-23 09:50:00', 3820, 4.30,
       '<p>探针每分钟回访一批关键页面，比对状态码、体积与首字节时间，产物被误删或被覆盖时先于访客发现。</p><h2>只监控关键页</h2><p>首页、每个一级栏目、最近七天的详情页抽样，数量控制在一百条以内。</p>',
       '{"brand":"globex","region":"huadong","price":2480.00,"stock":310,"featured":0,"specs":[{"key":"探测间隔","value":"60 秒"},{"key":"告警通道","value":"邮件 / Webhook"},{"key":"历史保留","value":"90 天"},{"key":"质保","value":"一年"}],"gallery":[{"url":"/uploads/demo/product-05.jpg","alt":"监控探针面板","width":1280,"height":720}],"download":{"url":"/uploads/demo/globex-monitor-m3.pdf","name":"M3 监控探针说明.pdf","size":188600,"mime":"application/pdf"}}', 300),
      ('globex-theme-studio', 'Globex 主题工作台',
       '把主题模板的编辑、预览与版本管理放在一起，改一行片段立刻看到影响面。',
       'Globex', 'huanan', 9800.00, 74, 0, 1,
       '/uploads/demo/product-06.jpg', 6, '2026-02-12 15:10:00', 2410, 4.50,
       '<p>把主题模板的编辑、预览与版本管理放在一起：改一行片段，立刻在本地预览里看到它影响到的所有页面。</p><h2>预览与正式产物的区别</h2><p>预览只走内存渲染，不写产物目录；确认之后才进入正式批次。</p>',
       '{"brand":"globex","region":"huanan","price":9800.00,"stock":74,"featured":0,"specs":[{"key":"模板校验","value":"编译期全量校验"},{"key":"预览方式","value":"内存渲染"},{"key":"版本管理","value":"按模板字节哈希"},{"key":"质保","value":"一年"}],"gallery":[{"url":"/uploads/demo/product-06.jpg","alt":"主题工作台界面","width":1280,"height":720}],"download":{"url":"/uploads/demo/globex-theme-studio.pdf","name":"主题工作台说明书.pdf","size":224800,"mime":"application/pdf"}}', 300),
      ('initech-archive-a7', 'Initech 归档一体机 A7',
       '给历史批次准备的归档一体机：保留最近几批完整产物，回滚只切一次软链。',
       'Initech', 'huabei', 21800.00, 18, 1, 0,
       '/uploads/demo/product-07.jpg', 7, '2026-01-05 08:40:00', 3340, 4.80,
       '<p>给历史批次准备的归档一体机：保留最近几批的完整产物，回滚只需要切一次软链。</p><h2>保留策略</h2><p>按批次保留，批次数由站点选项控制；超出后从最旧一批开始回收，回收记录写进发布报告。</p>',
       '{"brand":"initech","region":"huabei","price":21800.00,"stock":18,"featured":1,"specs":[{"key":"容量","value":"24TB"},{"key":"保留批次","value":"3 批"},{"key":"回滚方式","value":"软链切换"},{"key":"质保","value":"五年"}],"gallery":[{"url":"/uploads/demo/product-07.jpg","alt":"归档一体机","width":1280,"height":720}],"download":{"url":"/uploads/demo/initech-archive-a7.pdf","name":"A7 归档一体机说明.pdf","size":402200,"mime":"application/pdf"}}', 320),
      ('initech-search-box', 'Initech 站内搜索盒',
       '把静态搜索索引托管出去：分片按固定条数切好，命中完全在浏览器里完成。',
       'Initech', 'huadong', 4380.00, 96, 0, 0,
       '/uploads/demo/product-08.jpg', 8, '2026-02-25 13:30:00', 2070, 4.20,
       '<p>把静态搜索索引托管出去：索引分片按固定条数切好，前端按需拉取，命中完全在浏览器里完成。</p><h2>五万条是分水岭</h2><p>超过这个量级切接口模式，索引产物照旧生成，作为接口不可用时的降级路径。</p>',
       '{"brand":"initech","region":"huadong","price":4380.00,"stock":96,"featured":0,"specs":[{"key":"分片大小","value":"2000 条"},{"key":"压缩","value":"gzip"},{"key":"阈值","value":"50000 条"},{"key":"质保","value":"一年"}],"gallery":[{"url":"/uploads/demo/product-08.jpg","alt":"搜索索引分片示意","width":1280,"height":720}],"download":{"url":"/uploads/demo/initech-search-box.pdf","name":"站内搜索盒说明.pdf","size":152400,"mime":"application/pdf"}}', 300),
      ('initech-form-relay', 'Initech 表单中转站',
       '静态站没有后端，表单仍要能收：中转提交后 302 回原页并带上结果标记。',
       'Initech', 'huanan', 3280.00, 140, 0, 1,
       '/uploads/demo/product-09.jpg', 9, '2026-03-06 10:00:00', 2480, 4.40,
       '<p>静态站没有后端，表单仍要能收：跳转到中转站提交，落库之后再 302 回原页面并带上结果标记。</p><h2>无 JS 也能用</h2><p>原生表单提交即可完成，不依赖任何前端脚本。</p>',
       '{"brand":"initech","region":"huanan","price":3280.00,"stock":140,"featured":0,"specs":[{"key":"提交方式","value":"原生表单 POST"},{"key":"防刷","value":"限流 + 蜜罐字段"},{"key":"回跳","value":"带 form_ok / form_error"},{"key":"质保","value":"一年"}],"gallery":[{"url":"/uploads/demo/product-09.jpg","alt":"表单中转流程","width":1280,"height":720}],"download":{"url":"/uploads/demo/initech-form-relay.pdf","name":"表单中转站说明.pdf","size":142600,"mime":"application/pdf"}}', 290)
  ) as v(slug, title, summary, brand, region, price, stock, top, recommend, cover, sort,
         publish_time, view_count, rating_avg, content, data, word_count)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'product'
                      and c.slug = v.slug and c.deleted = 0);

-- 产品的相关产品：同品牌里除自己之外的两件（RELATION 存产品 id 数组，顺序即录入顺序）
update cms_content c
   set data = jsonb_set(coalesce(c.data, '{}'::jsonb), '{relatedProducts}',
                        (select jsonb_agg(x.id order by x.id)
                           from (select x.id
                                   from cms_content x
                                  where x.site_id = c.site_id and x.type_code = 'product' and x.deleted = 0
                                    and x.slug <> c.slug
                                    and x.data ->> 'brand' = c.data ->> 'brand'
                                  order by x.id
                                  limit 2) x)),
       update_time = c.publish_time + interval '4 hours'
  from cms_site s
 where s.code = 'demo' and s.deleted = 0
   and c.site_id = s.id and c.type_code = 'product' and c.deleted = 0
   and exists (select 1 from cms_content x
                where x.site_id = c.site_id and x.type_code = 'product' and x.deleted = 0
                  and x.slug <> c.slug
                  and x.data ->> 'brand' = c.data ->> 'brand');

-- 文章的相关产品（RELATION 用产品 id 数组）：同区域取前两件产品
update cms_content c
   set data = jsonb_set(coalesce(c.data, '{}'::jsonb), '{relatedProducts}',
                        (select jsonb_agg(x.id order by x.id)
                           from (select x.id
                                   from cms_content x
                                  where x.site_id = c.site_id and x.type_code = 'product' and x.deleted = 0
                                    and x.data ->> 'region' = c.data ->> 'region'
                                  order by x.id
                                  limit 2) x))
  from cms_site s
 where s.code = 'demo' and s.deleted = 0
   and c.site_id = s.id and c.type_code = 'article' and c.deleted = 0
   and exists (select 1 from cms_content x
                where x.site_id = c.site_id and x.type_code = 'product' and x.deleted = 0
                  and x.data ->> 'region' = c.data ->> 'region');

-- 6.6 单页（about / contact 各一条；single 类型这里不插内容，
-- 让"关于我们 / 联系我们"两个菜单项落在 about / contact 上，不落到内置 single）
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, cover, status, sort,
                         top, recommend, publish_time, view_count, content_format, content, data,
                         word_count, view_count_day, view_count_week, comment_count,
                         rating_avg, rating_count)
select s.id, v.type_code, 0, v.slug, v.title, v.summary, null, 'PUBLISHED', 1,
       0, 0, '2025-12-20 09:00:00'::timestamp, v.view_count, 'RICHTEXT', v.content, v.data::jsonb,
       v.word_count, 0, 0, 0, 0, 0
  from cms_site s
  cross join (values
      ('about', 'about', '关于灵久', '一支把内容与发布链路放在一起做的小团队。',
       '<p>灵久做的是内容基础设施：内容怎么组织、怎么被检索、怎么变成文件，以及文件怎么被运维。</p><h2>我们在意什么</h2><ul><li>把复杂度放在构建期，而不是访客的请求里；</li><li>规则写在契约里，模板与文档都不许再定义一遍；</li><li>每一条"代价如实记录"都比一句"最佳实践"有用。</li></ul><h2>这个站点</h2><p>你正在看的这个站点就是演示数据本身：七种内容类型、三个作者、九件产品、十五个章节，全部可以直接发布成静态产物。</p>',
       '{"subtitle":"把内容与发布链路放在一起做"}', 210, 1240),
      ('contact', 'contact', '联系我们', '工作日的 9:30 到 18:30 都有人在。',
       '<p>商务合作、技术支持与内容投稿都走同一个邮箱，工作日一天内回复。</p><h2>联系方式</h2><ul><li>邮箱：demo@lingjiuw.local</li><li>电话：010-88880000（工作日 9:30–18:30）</li><li>地址：北京市朝阳区示范围 88 号 3 号楼 5 层</li></ul><h2>到访</h2><p>来访请提前一天邮件预约，前台需要登记。</p>',
       '{"subtitle":"工作日 9:30–18:30 都有人在"}', 168, 860)
  ) as v(type_code, slug, title, summary, content, data, word_count, view_count)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = v.type_code
                      and c.slug = v.slug and c.deleted = 0);

-- ---------------------------------------------------------------------------
-- 7. 关联表：分类 / 标签 / 字段索引
-- ---------------------------------------------------------------------------

-- 7.1 分类关联（§2.4）
-- 每篇文章恰一个 primary（主分类决定 categoryUrl 与面包屑），其中 3 篇再挂一个
-- news 作为 secondary；产品按品牌分到案例或新闻；书籍与章节不挂分类。
insert into cms_content_category (content_id, category_id, dimension)
select c.id,
       (select cat.id from cms_category cat
         where cat.site_id = c.site_id and cat.deleted = 0 and cat.slug = v.cat_slug),
       'primary'
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0
  cross join (values
      (1,  'news'), (2,  'tech'), (3,  'tech'), (4,  'tech'), (5,  'case'),
      (6,  'news'), (7,  'tech'), (8,  'news'), (9,  'tech'), (10, 'case'),
      (11, 'news'), (12, 'tech'), (13, 'case'), (14, 'news')
  ) as v(seq, cat_slug)
 where s.code = 'demo' and s.deleted = 0
   and c.type_code = 'article'
   and c.sort = v.seq
   and not exists (select 1 from cms_content_category cc
                    where cc.content_id = c.id and cc.dimension = 'primary' and cc.deleted = 0);

insert into cms_content_category (content_id, category_id, dimension)
select c.id,
       (select cat.id from cms_category cat
         where cat.site_id = c.site_id and cat.deleted = 0 and cat.slug = 'news'),
       'secondary'
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0
 where s.code = 'demo' and s.deleted = 0
   and c.type_code = 'article'
   and c.sort in (1, 5, 10)
   and not exists (select 1 from cms_content_category cc
                    where cc.content_id = c.id and cc.dimension = 'secondary' and cc.deleted = 0);

insert into cms_content_category (content_id, category_id, dimension)
select c.id,
       (select cat.id from cms_category cat
         where cat.site_id = c.site_id and cat.deleted = 0
           and cat.slug = case when c.data ->> 'brand' = 'initech' then 'news' else 'case' end),
       'primary'
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0
 where s.code = 'demo' and s.deleted = 0
   and c.type_code = 'product'
   and not exists (select 1 from cms_content_category cc
                    where cc.content_id = c.id and cc.dimension = 'primary' and cc.deleted = 0);

-- 7.2 标签关联（§2.4）——按内容 sort 轮转，保证每个标签都挂到内容上
insert into cms_content_tag (content_id, tag_id)
select c.id, t.id
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'article'
  join cms_tag t on t.site_id = s.id and t.deleted = 0
 where s.code = 'demo' and s.deleted = 0
   and t.slug = any (case c.sort % 5
                         when 0 then array['java', 'template', 'cms']
                         when 1 then array['java', 'static']
                         when 2 then array['template', 'design', 'cms']
                         when 3 then array['static', 'cms']
                         else array['java', 'static', 'design'] end)
   and not exists (select 1 from cms_content_tag ct
                    where ct.content_id = c.id and ct.tag_id = t.id and ct.deleted = 0);

-- article 的 TAGS 自定义字段：data.tags 存标签 id 数组，取值与上面挂进
-- cms_content_tag 的同一套（否则"字段值"与"标签关联"两边对不上，索引行也会缺）
update cms_content c
   set data = jsonb_set(coalesce(c.data, '{}'::jsonb), '{tags}',
                        (select jsonb_agg(ct.tag_id order by ct.tag_id)
                           from cms_content_tag ct
                          where ct.content_id = c.id and ct.deleted = 0))
  from cms_site s
 where s.code = 'demo' and s.deleted = 0
   and c.site_id = s.id and c.type_code = 'article' and c.deleted = 0
   and exists (select 1 from cms_content_tag ct where ct.content_id = c.id and ct.deleted = 0);

insert into cms_content_tag (content_id, tag_id)
select c.id, t.id
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0
  cross join (values ('product', array['static', 'cms']),
                     ('book',    array['java', 'template']),
                     ('author',  array['design', 'cms']),
                     ('about',   array['cms']),
                     ('contact', array['design']),
                     ('chapter', array['java'])
              ) as v(type_code, slugs)
  join cms_tag t on t.site_id = s.id and t.deleted = 0 and t.slug = any (v.slugs)
 where s.code = 'demo' and s.deleted = 0
   and c.type_code = v.type_code
   and not exists (select 1 from cms_content_tag ct
                    where ct.content_id = c.id and ct.tag_id = t.id and ct.deleted = 0);

-- 7.3 字段索引（§2.5）
-- 只给 cms_field.indexed = 1 的字段写：标量字段 value_key='default'，多值字段每个取值一行；
-- value_type 写字段定义里的类型名，值按类型落到 num_value / str_value（其余留空）。
-- where / orderby / facet 页面只读这张表，漏一行就等于"这个取值筛不到内容"。

-- article：brand / region / price / weight / featured（标量）+ tags / readingLevel（多值）
insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, v.field_code, 'default', v.value_type,
       v.num_value, v.str_value, null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'article'
  cross join lateral (values
      ('brand',    'TEXT',    null::numeric, (c.data ->> 'brand')),
      ('region',   'ENUM',    null::numeric, (c.data ->> 'region')),
      ('price',    'DECIMAL', nullif(c.data ->> 'price', '')::numeric,  null::varchar),
      ('weight',   'INT',     nullif(c.data ->> 'weight', '')::numeric, null::varchar),
      ('featured', 'BOOL',    (c.data ->> 'featured')::numeric,         null::varchar)
  ) as v(field_code, value_type, num_value, str_value)
 where s.code = 'demo' and s.deleted = 0
   and (v.num_value is not null or v.str_value is not null)
   and not exists (select 1 from cms_content_index ci
                    where ci.content_id = c.id and ci.field_code = v.field_code
                      and ci.value_key = 'default' and ci.deleted = 0);

insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, 'tags', t.value, 'TAGS', null, t.value, null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'article'
  cross join lateral jsonb_array_elements_text(c.data -> 'tags') as t(value)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content_index ci
                    where ci.content_id = c.id and ci.field_code = 'tags'
                      and ci.value_key = t.value and ci.deleted = 0);

insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, 'readingLevel', l.value, 'ENUM_MULTI', null, l.value, null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'article'
  cross join lateral jsonb_array_elements_text(c.data -> 'readingLevel') as l(value)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content_index ci
                    where ci.content_id = c.id and ci.field_code = 'readingLevel'
                      and ci.value_key = l.value and ci.deleted = 0);

insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, 'relatedProducts', r.value, 'RELATION', r.value::numeric, null, null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'article'
  cross join lateral jsonb_array_elements_text(c.data -> 'relatedProducts') as r(value)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content_index ci
                    where ci.content_id = c.id and ci.field_code = 'relatedProducts'
                      and ci.value_key = r.value and ci.deleted = 0);

-- product：brand / region / price / stock / featured 全是标量，一次写完
insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, v.field_code, 'default', v.value_type,
       v.num_value, v.str_value, null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'product'
  cross join lateral (values
      ('brand',    'ENUM',    null::numeric, (c.data ->> 'brand')),
      ('region',   'ENUM',    null::numeric, (c.data ->> 'region')),
      ('price',    'DECIMAL', nullif(c.data ->> 'price', '')::numeric,  null::varchar),
      ('stock',    'INT',     nullif(c.data ->> 'stock', '')::numeric,  null::varchar),
      ('featured', 'BOOL',    (c.data ->> 'featured')::numeric,         null::varchar)
  ) as v(field_code, value_type, num_value, str_value)
 where s.code = 'demo' and s.deleted = 0
   and (v.num_value is not null or v.str_value is not null)
   and not exists (select 1 from cms_content_index ci
                    where ci.content_id = c.id and ci.field_code = v.field_code
                      and ci.value_key = 'default' and ci.deleted = 0);

insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, 'relatedProducts', r.value, 'RELATION', r.value::numeric, null, null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'product'
  cross join lateral jsonb_array_elements_text(c.data -> 'relatedProducts') as r(value)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content_index ci
                    where ci.content_id = c.id and ci.field_code = 'relatedProducts'
                      and ci.value_key = r.value and ci.deleted = 0);

-- book：author（TEXT）+ bookStatus（ENUM）+ tags（TAGS）
insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, v.field_code, 'default', v.value_type, null, v.str_value, null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'book'
  cross join lateral (values
      ('author',     'TEXT', (c.data ->> 'author')),
      ('bookStatus', 'ENUM', (c.data ->> 'bookStatus'))
  ) as v(field_code, value_type, str_value)
 where s.code = 'demo' and s.deleted = 0
   and v.str_value is not null
   and not exists (select 1 from cms_content_index ci
                    where ci.content_id = c.id and ci.field_code = v.field_code
                      and ci.value_key = 'default' and ci.deleted = 0);

insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, 'tags', t.value, 'TAGS', null, t.value, null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'book'
  cross join lateral jsonb_array_elements_text(c.data -> 'tags') as t(value)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content_index ci
                    where ci.content_id = c.id and ci.field_code = 'tags'
                      and ci.value_key = t.value and ci.deleted = 0);

-- chapter：volume
insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, 'volume', 'default', 'TEXT', null, (c.data ->> 'volume'), null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'chapter'
 where s.code = 'demo' and s.deleted = 0
   and (c.data ->> 'volume') is not null
   and not exists (select 1 from cms_content_index ci
                    where ci.content_id = c.id and ci.field_code = 'volume'
                      and ci.value_key = 'default' and ci.deleted = 0);

-- author：honor
insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, 'honor', 'default', 'TEXT', null, (c.data ->> 'honor'), null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'author'
 where s.code = 'demo' and s.deleted = 0
   and (c.data ->> 'honor') is not null
   and not exists (select 1 from cms_content_index ci
                    where ci.content_id = c.id and ci.field_code = 'honor'
                      and ci.value_key = 'default' and ci.deleted = 0);

-- ---------------------------------------------------------------------------
-- 8. 榜单计数（§2.3 的 view_count_day / view_count_week）
--
-- 两列是滚动 24 小时 / 7 天的有效浏览数，日榜周榜的排序依据（§9.4）。
-- 演示数据里按"总量的一部分"给：越老的内容（id 越小）当期占比越低，这样
-- 榜单顺序与 publishTime 顺序不同——pages.static 里那个 rank 页要的正是这个效果。
-- ---------------------------------------------------------------------------
update cms_content c
   set view_count_day  = greatest(1, round(c.view_count * 0.22 * (1 + (c.id % 5) * 0.15)
                                       / (1 + (10 - least(c.id, 10)) * 0.25))::bigint),
       view_count_week = greatest(1, round(c.view_count * 0.55 * (1 + (c.id % 3) * 0.12)
                                       / (1 + (10 - least(c.id, 10)) * 0.12))::bigint)
  from cms_site s
 where s.code = 'demo' and s.deleted = 0
   and c.site_id = s.id
   and c.deleted = 0
   and c.status = 'PUBLISHED'
   and c.type_code in ('article', 'product', 'book', 'chapter');

-- ---------------------------------------------------------------------------
-- 9. 导航菜单与菜单项（§2.4）
--
-- 迁移 11 的菜单播种是"遍历执行那一刻已存在的站点"，因此 demo 站点如果不是在那之前
-- 建的，就没有 main 菜单容器——这里先补容器（按 (site_id, code) 判存），再补菜单项。
-- 菜单项的 kind 尽量指向真实对象，这样引擎解析出来的 URL 与页面清单一致，
-- "当前项高亮"才有东西可比：
--   type     → 产品 / 书籍 / 作者三个类型列表页
--   category → 新闻 / 案例两个分类索引页
--   content  → 关于我们 / 联系我们两个单页
--   url      → 首页（`/` 不是任何对象的列表页）
-- 菜单项没有天然唯一键，判存按 (menu_id, label) 做，避免重复执行时插两遍。
-- ---------------------------------------------------------------------------
insert into cms_menu (site_id, code, name, status, sort)
select s.id, 'main', '主导航', 1, 1
  from cms_site s
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_menu m
                    where m.site_id = s.id and m.code = 'main' and m.deleted = 0);

insert into cms_menu_item (menu_id, parent_id, label, kind, ref_id, ref_code, url, target, rel, visible, sort)
select m.id, 0, v.label, v.kind,
       case v.kind
           when 'category' then (select cat.id from cms_category cat
                                  where cat.site_id = s.id and cat.slug = v.ref and cat.deleted = 0)
           when 'content'  then (select c.id from cms_content c
                                  where c.site_id = s.id and c.type_code = v.ref_type
                                    and c.slug = v.ref and c.deleted = 0)
       end,
       case when v.kind = 'type' then v.ref end,
       case when v.kind = 'url' then v.ref end,
       null, null, 1, v.sort
  from cms_site s
  join cms_menu m on m.site_id = s.id and m.code = 'main' and m.deleted = 0
  cross join (values
      ('首页',   'url',      '/',        null,       1),
      ('新闻',   'category', 'news',     null,       2),
      ('产品',   'type',     'product',  'product',  3),
      ('书籍',   'type',     'book',     'book',     4),
      ('作者',   'type',     'author',   'author',   5),
      ('关于',   'content',  'about',    'about',    6),
      ('联系',   'content',  'contact',  'contact',  7)
  ) as v(label, kind, ref, ref_type, sort)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_menu_item mi
                    where mi.menu_id = m.id and mi.label = v.label and mi.deleted = 0);
-- ---------------------------------------------------------------------------
-- 10. 自检提醒（不在迁移里跑断言，断言放在验证脚本里）
--
-- 这个文件跑完之后，demo 站应当具备：
--   cms_content_type   8 行（article / product / book / chapter / author / about / contact / single）
--   cms_field         30 行（其中 indexed=1 的 15 个字段定义，全部有对应的 cms_content_index 行）
--   cms_content       46 行（article 14 / product 9 / book 3 / chapter 15 / author 3 / about 1 / contact 1）
--   cms_content_category 26 行（14 + 9 个主分类 + 3 个副分类）
--   cms_content_index 约 260 行；cms_content_tag 83 行
--   cms_menu_item       7 行（首页 / 新闻 / 产品 / 书籍 / 作者 / 关于 / 联系）
-- 任何一条对不上，先看第 5 节（分类）与第 6 节（内容）的 where 条件是否真的命中了行。
-- ---------------------------------------------------------------------------
