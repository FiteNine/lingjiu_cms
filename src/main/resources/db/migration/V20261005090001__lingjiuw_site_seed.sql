-- =============================================================================
-- lingjiuw 站点：凌久网官网
-- 契约：target/research/design-lingjiuw-site.md §1–§3.4
-- 内容原文：target/research/site-inventory.md（逐字清单，含站点配置 / 重复集合 / 每页 SEO）
--
-- 这个站点是从 backend/sites/lingjiuw.cn/www/ 那份手写静态站反推出来的：
--   站点信息 + 发布选项 §1–§2、内容类型/字段/内容 §3、字段索引 §3.3、导航菜单 §3.4。
-- 与迁移 20261003090001（demo 站点）同样的三条写法约定：
--   1. 先 insert ... select ... where not exists 判存，再 update 覆盖成目标值；
--   2. 内容一律先 insert 再按 slug 反查 id 做后续 update，文件里不写死 id；
--   3. 所有 INSERT 写全列名；jsonb 列的字符串字面量一律带 ::jsonb。
--
-- 站点目录 root_dir = 'lingjiuw.cn'（对应 backend/sites/lingjiuw.cn），
-- 主题 theme = 'lingjiuw'（对应 <站点目录>/template/lingjiuw/）。本文件不建目录。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 站点：code='lingjiuw'
--
-- 与官网原文的对应关系（site-inventory.md A 节）：
--   name/domain/logo/og_image/icp/contact_phone/contact_email 都是官网里出现过的**逐字**取值；
--   description 放页头副标题「技术解决方案」，seo_description 放首页 meta description；
--   keywords **刻意留空**：官网正文注释写明不写 meta keywords（百度官方字段里没有它，
--   谷歌明确忽略），模板也因此不输出这个标签。
-- ---------------------------------------------------------------------------
insert into cms_site (name, code, domain, description, keywords, seo_description,
                      root_dir, icp, contact_phone, contact_email, logo, og_image, default_cover,
                      lang, theme, protocol, status, is_default)
select '凌久网', 'lingjiuw', 'www.lingjiuw.cn',
       '技术解决方案',
       '',
       '凌久网：微信/抖音/支付宝小程序开发、网站建设与定制系统开发。免费评估需求，工作日 24 小时内答复。',
       'lingjiuw.cn', '粤ICP备2026130524号-1', '176 7703 5288', '1661675037@qq.com',
       '/assets/img/logo.svg', '/assets/img/og-cover.png', '/assets/img/og-cover.png',
       'zh-CN', 'lingjiuw', 'https', 1, 0
 where not exists (select 1 from cms_site where code = 'lingjiuw' and deleted = 0);

update cms_site
   set name            = '凌久网',
       domain          = 'www.lingjiuw.cn',
       description     = '技术解决方案',
       keywords        = '',
       seo_description = '凌久网：微信/抖音/支付宝小程序开发、网站建设与定制系统开发。免费评估需求，工作日 24 小时内答复。',
       root_dir        = 'lingjiuw.cn',
       icp             = '粤ICP备2026130524号-1',
       contact_phone   = '176 7703 5288',
       contact_email   = '1661675037@qq.com',
       logo            = '/assets/img/logo.svg',
       og_image        = '/assets/img/og-cover.png',
       default_cover   = '/assets/img/og-cover.png',
       lang            = 'zh-CN',
       theme           = 'lingjiuw',
       protocol        = 'https',
       status          = 1
 where code = 'lingjiuw' and deleted = 0;

-- ---------------------------------------------------------------------------
-- 2. 发布选项
--
-- 2.1 官网自己的业务选项（模板里用 [field:site.option.<code>/] 读）。
--     为什么放在发布选项而不是新开一张表：它们的数量与取值只由这个站点决定，
--     而"站点级的一串配置"正是发布选项表的语义；新增一条不用改表结构、不用发迁移。
--     **单一事实源**：电话、微信号、邮箱、ICP、更新日期在官网里各出现 4–10 次，
--     这里各只有一份，模板全部从这一份渲染。
-- 2.2 标准选项的 lingjiuw 取值：官网没有栏目页/标签页/归档/筛选/搜索/feed，
--     对应的 page.* 开关全部关掉——关掉之后计划期就不会去找那些模板。
-- ---------------------------------------------------------------------------
with opts(option_code, value) as (
    values
        -- 品牌
        ('brand.sub',              '技术解决方案'),
        ('brand.footerSlogan',     '把技术做好，让系统耐用'),
        ('brand.themeColor',       '#17324F'),
        ('brand.ogImageAlt',       '凌久网：小程序开发、网站建设、定制系统与长期运维'),
        ('brand.orgDescription',   '做技术交付的团队：小程序、网站、定制系统、数据集成，以及上线之后的长期运维。'),
        -- 联系方式（同一事实的多种形态：显示文本 / tel: 链接 / JSON-LD 里的国际写法）
        ('contact.wechat',         'lingjiuw_cn'),
        ('contact.wechatNote',     '（加好友请备注"官网咨询"）'),
        ('contact.wechatNotePrivacy', '（加好友请备注"隐私政策"）'),
        ('contact.phoneTel',       '+8617677035288'),
        ('contact.phoneJsonLd',    '+86-176-7703-5288'),
        ('contact.hours',          '工作日 9:00–19:00'),
        ('contact.heading',        '说说你的需求'),
        ('contact.note',           '微信、电话、邮件都可以。工作日 24 小时内答复，着急的事直接打电话。'),
        ('contact.meta',           '电话 176 7703 5288，微信 lingjiuw_cn。工作日 24 小时内答复。'),
        ('contact.qrImage',        '/assets/img/wechat-qr.png'),
        ('contact.qrAlt',          '凌久网微信二维码，微信扫一扫添加 lingjiuw_cn 为好友'),
        ('contact.qrCaption',      '用微信扫一扫，添加好友'),
        -- 合规与主体
        ('legal.orgName',          '佛山市高明区镜知有物网络技术服务中心（个体工商户）'),
        ('legal.icpUrl',           'https://beian.miit.gov.cn/'),
        -- SEO
        ('seo.applicableDevice',   'pc,mobile'),
        ('seo.author',             '凌久网'),
        ('seo.robotsDoc',          'index,follow'),
        ('seo.verifyBaidu',        ''),   -- 空 = 不输出该 meta（站长平台验证时填上即可）
        ('seo.verifyGoogle',       ''),   -- 空 = 不输出该 meta
        -- 首屏「已连续交付运行」的起点（原来硬编码在 main.js 里）与表单收件地址
        ('home.uptimeStart',       '2018-06-01T00:00:00+08:00'),
        ('home.formMailto',        '1661675037@qq.com'),
        -- 标准选项：官网的页面形态
        ('page.category',          '0'),
        ('page.tag',               '0'),
        ('page.taglist',           '0'),
        ('page.archive',           '0'),
        ('page.author',            '0'),
        ('page.facet',             '0'),
        ('page.search',            '0'),
        ('page.feed',              '0'),
        ('page.redirect',          '0'),
        ('publish.mode',           'full'),
        ('sitemap.shardSize',      '10000'),
        -- 站点声明的静态页：nginx 的 error_page 500 502 503 504 → /50x.html。
        -- noindex=1 有两层含义：这一页自己带 noindex,follow，且不进 sitemap
        -- （它被 nginx 标成 internal，爬虫根本取不到，进 sitemap 就是死链）。
        ('pages.static',
         '[{"code":"50x","url":"/50x.html","template":"50x.html","noindex":1}]')
),
ins as (
    insert into cms_site_publish_option (site_id, option_code, value)
    select s.id, o.option_code, o.value
      from cms_site s
      cross join opts o
     where s.code = 'lingjiuw' and s.deleted = 0
       and not exists (select 1 from cms_site_publish_option p
                        where p.site_id = s.id and p.option_code = o.option_code and p.deleted = 0)
    returning option_code
)
update cms_site_publish_option p
   set value = o.value
  from cms_site s, opts o
 where s.code = 'lingjiuw' and s.deleted = 0
   and p.site_id = s.id
   and p.option_code = o.option_code
   and p.deleted = 0;

-- ---------------------------------------------------------------------------
-- 3. 内容类型
--
-- 七个类型里只有 landing / doc 有 URL（详情模板分别 landing.html / doc.html），
-- 其余五个是"只被页面引用、自己不出版面"的集合：
--   home       单例，装首页各区块的标题与导语（SINGLE 且 detail_url_pattern 为空 → 不出版面）
--   service    15 项服务，首页服务索引与顶部下拉层共用
--   step       合作流程（首页 4 条顶级 + 每个落地页 4 条子节点）
--   faq        常见问题（同上，首页 5 条 + 落地页 5 条）
--   status     运行看板 4 行
-- list_url_pattern 一律为空：类型列表页是"资讯站"的形态，官网不需要。
-- ---------------------------------------------------------------------------
insert into cms_content_type (site_id, code, name, kind, hierarchical,
                              detail_url_pattern, list_url_pattern, per_page, status, sort)
select s.id, v.code, v.name, v.kind, v.hierarchical,
       v.detail_url_pattern, v.list_url_pattern, v.per_page, 1, v.sort
  from cms_site s
  cross join (values
      ('home',    '首页文案',   'SINGLE',  0, null,                 null, 20, 1),
      ('service', '服务项',     'CONTENT', 0, null,                 null, 20, 2),
      ('step',    '合作流程',   'CONTENT', 0, null,                 null, 20, 3),
      ('faq',     '常见问题',   'CONTENT', 0, null,                 null, 20, 4),
      ('status',  '运行看板',   'CONTENT', 0, null,                 null, 20, 5),
      ('landing', '服务落地页', 'CONTENT', 0, '/{slug}/',           null, 20, 6),
      ('doc',     '文档页',     'CONTENT', 0, '/{slug}/',           null, 20, 7)
  ) as v(code, name, kind, hierarchical, detail_url_pattern, list_url_pattern, per_page, sort)
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_content_type t
                    where t.site_id = s.id and t.code = v.code and t.deleted = 0);

update cms_content_type t
   set name               = v.name,
       kind               = v.kind,
       hierarchical       = v.hierarchical,
       detail_url_pattern = v.detail_url_pattern,
       list_url_pattern   = v.list_url_pattern,
       per_page           = v.per_page,
       detail_template    = v.detail_template,
       sort_field         = v.sort_field,
       sort_order         = v.sort_order,
       status             = 1,
       sort               = v.sort
  from cms_site s
  cross join (values
      ('home',    '首页文案',   'SINGLE',  0, null,       null, 20, null,          null,  null, 1),
      ('service', '服务项',     'CONTENT', 0, null,       null, 20, null,          'sort', 'asc', 2),
      ('step',    '合作流程',   'CONTENT', 0, null,       null, 20, null,          'sort', 'asc', 3),
      ('faq',     '常见问题',   'CONTENT', 0, null,       null, 20, null,          'sort', 'asc', 4),
      ('status',  '运行看板',   'CONTENT', 0, null,       null, 20, null,          'sort', 'asc', 5),
      ('landing', '服务落地页', 'CONTENT', 0, '/{slug}/', null, 20, 'landing.html', null,  null, 6),
      ('doc',     '文档页',     'CONTENT', 0, '/{slug}/', null, 20, 'doc.html',     null,  null, 7)
  ) as v(code, name, kind, hierarchical, detail_url_pattern, list_url_pattern, per_page,
         detail_template, sort_field, sort_order, sort)
 where s.code = 'lingjiuw' and s.deleted = 0
   and t.site_id = s.id
   and t.code = v.code
   and t.deleted = 0;

-- ---------------------------------------------------------------------------
-- 4. 字段定义
--
-- 两条承重约定：
--   * service.group 必须 indexed=1 —— 首页服务索引与下拉层都靠 where='group:eq:xxx'
--     分栏，而 where 只读索引表（§2.5）；漏勾会在计划期报 E2007。
--   * 富文本正文（scenesBody / costBody / deliverBody）必须 raw=1 —— raw=0 会把
--     HTML 当文本转义，页面上直接出现一排 <p> 源码。
-- 字段 code 避开六个保留名（site / channel / page / param / query / item）。
-- home 与 landing 都有 formTopic：联系表单的「当前咨询」默认值两页不同，
-- 由当前页内容的这个字段给出，联系区片段因此可以两页共用。
-- ---------------------------------------------------------------------------
insert into cms_field (site_id, type_code, code, label, field_type, raw, searchable, indexed, sort)
select s.id, v.type_code, v.code, v.label, v.field_type, v.raw, v.searchable, v.indexed, v.sort
  from cms_site s
  cross join (values
      -- home：首页各区块的文案（标题 / 导语 / 尾注）
      ('home',    'heroKeywords',     '首屏关键词行',     'TEXT',     0, 0, 0, 1),
      ('home',    'heroLine1',        '首屏标题第一行',   'TEXT',     0, 0, 0, 2),
      ('home',    'heroLine2',        '首屏标题第二行',   'TEXT',     0, 0, 0, 3),
      ('home',    'heroLead',         '首屏导语',         'TEXTAREA', 0, 0, 0, 4),
      ('home',    'panelTitle',       '交付示意图标题',   'TEXT',     0, 0, 0, 5),
      ('home',    'panelStatus',      '面板系统状态',     'TEXT',     0, 0, 0, 6),
      ('home',    'panelUptimeLabel', '面板运行时长标签', 'TEXT',     0, 0, 0, 7),
      ('home',    'servicesTitle',    '服务区标题',       'TEXT',     0, 0, 0, 8),
      ('home',    'servicesNote',     '服务区说明',       'TEXT',     0, 0, 0, 9),
      ('home',    'servicesHint',     '服务区尾注',       'TEXT',     0, 0, 0, 10),
      ('home',    'processTitle',     '流程区标题',       'TEXT',     0, 0, 0, 11),
      ('home',    'processNote',      '流程区说明',       'TEXT',     0, 0, 0, 12),
      ('home',    'statusTitle',      '看板区标题',       'TEXT',     0, 0, 0, 13),
      ('home',    'statusNote',       '看板区说明',       'TEXT',     0, 0, 0, 14),
      ('home',    'statusSummary',    '看板汇总句',       'TEXT',     0, 0, 0, 15),
      ('home',    'statusFoot',       '看板页脚说明',     'TEXT',     0, 0, 0, 16),
      ('home',    'faqTitle',         '问答区标题',       'TEXT',     0, 0, 0, 17),
      ('home',    'faqNote',          '问答区说明',       'TEXT',     0, 0, 0, 18),
      ('home',    'formTopic',        '表单默认咨询主题', 'TEXT',     0, 0, 0, 19),
      -- service：一项服务
      ('service', 'group',            '服务分类',         'ENUM',     0, 0, 1, 1),
      ('service', 'icon',             '图标',             'TEXT',     0, 0, 0, 2),
      ('service', 'topic',            '咨询主题',         'TEXT',     0, 0, 0, 3),
      ('service', 'detailUrl',        '详情页地址',       'TEXT',     0, 0, 0, 4),
      ('service', 'detailLabel',      '详情链接文案',     'TEXT',     0, 0, 0, 5),
      -- step / faq / status
      ('step',    'body',             '正文',             'TEXTAREA', 0, 0, 0, 1),
      ('faq',     'answer',           '答案',             'TEXTAREA', 0, 0, 0, 1),
      ('status',  'client',           '客户',             'TEXT',     0, 0, 0, 1),
      ('status',  'startDate',        '上线日期',         'DATE',     0, 0, 0, 2),
      ('status',  'note',             '近况',             'TEXT',     0, 0, 0, 3),
      ('status',  'state',            '状态',             'ENUM',     0, 0, 0, 4),
      -- landing：一个服务落地页
      ('landing', 'lead',             '首屏导语',         'TEXTAREA', 0, 0, 0, 1),
      ('landing', 'ogTitle',          '分享标题',         'TEXT',     0, 0, 0, 2),
      ('landing', 'scenesTitle',      '场景区标题',       'TEXT',     0, 0, 0, 3),
      ('landing', 'scenesNote',       '场景区说明',       'TEXT',     0, 0, 0, 4),
      ('landing', 'scenesBody',       '场景正文',         'RICHTEXT', 1, 1, 0, 5),
      ('landing', 'stepsTitle',       '流程区标题',       'TEXT',     0, 0, 0, 6),
      ('landing', 'stepsNote',        '流程区说明',       'TEXT',     0, 0, 0, 7),
      ('landing', 'costTitle',        '价格区标题',       'TEXT',     0, 0, 0, 8),
      ('landing', 'costNote',         '价格区说明',       'TEXT',     0, 0, 0, 9),
      ('landing', 'costBody',         '价格正文',         'RICHTEXT', 1, 1, 0, 10),
      ('landing', 'deliverTitle',     '交付区标题',       'TEXT',     0, 0, 0, 11),
      ('landing', 'deliverNote',      '交付区说明',       'TEXT',     0, 0, 0, 12),
      ('landing', 'deliverBody',      '交付正文',         'RICHTEXT', 1, 1, 0, 13),
      ('landing', 'faqTitle',         '问答区标题',       'TEXT',     0, 0, 0, 14),
      ('landing', 'faqNote',          '问答区说明',       'TEXT',     0, 0, 0, 15),
      ('landing', 'formTopic',        '表单默认咨询主题', 'TEXT',     0, 0, 0, 16)
  ) as v(type_code, code, label, field_type, raw, searchable, indexed, sort)
 where s.code = 'lingjiuw' and s.deleted = 0
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
      ('home',    'heroKeywords',     '首屏关键词行',     'TEXT',     null,                                                   0, 0, 0, 1),
      ('home',    'heroLine1',        '首屏标题第一行',   'TEXT',     null,                                                   0, 0, 0, 2),
      ('home',    'heroLine2',        '首屏标题第二行',   'TEXT',     null,                                                   0, 0, 0, 3),
      ('home',    'heroLead',         '首屏导语',         'TEXTAREA', null,                                                   0, 0, 0, 4),
      ('home',    'panelTitle',       '交付示意图标题',   'TEXT',     null,                                                   0, 0, 0, 5),
      ('home',    'panelStatus',      '面板系统状态',     'TEXT',     null,                                                   0, 0, 0, 6),
      ('home',    'panelUptimeLabel', '面板运行时长标签', 'TEXT',     null,                                                   0, 0, 0, 7),
      ('home',    'servicesTitle',    '服务区标题',       'TEXT',     null,                                                   0, 0, 0, 8),
      ('home',    'servicesNote',     '服务区说明',       'TEXT',     null,                                                   0, 0, 0, 9),
      ('home',    'servicesHint',     '服务区尾注',       'TEXT',     null,                                                   0, 0, 0, 10),
      ('home',    'processTitle',     '流程区标题',       'TEXT',     null,                                                   0, 0, 0, 11),
      ('home',    'processNote',      '流程区说明',       'TEXT',     null,                                                   0, 0, 0, 12),
      ('home',    'statusTitle',      '看板区标题',       'TEXT',     null,                                                   0, 0, 0, 13),
      ('home',    'statusNote',       '看板区说明',       'TEXT',     null,                                                   0, 0, 0, 14),
      ('home',    'statusSummary',    '看板汇总句',       'TEXT',     null,                                                   0, 0, 0, 15),
      ('home',    'statusFoot',       '看板页脚说明',     'TEXT',     null,                                                   0, 0, 0, 16),
      ('home',    'faqTitle',         '问答区标题',       'TEXT',     null,                                                   0, 0, 0, 17),
      ('home',    'faqNote',          '问答区说明',       'TEXT',     null,                                                   0, 0, 0, 18),
      ('home',    'formTopic',        '表单默认咨询主题', 'TEXT',     null,                                                   0, 0, 0, 19),
      ('service', 'group',            '服务分类',         'ENUM',     'dev:应用开发,auto:效率与自动化,data:集成与数据,ops:顾问与运维', 0, 0, 1, 1),
      ('service', 'icon',             '图标',             'TEXT',     null,                                                   0, 0, 0, 2),
      ('service', 'topic',            '咨询主题',         'TEXT',     null,                                                   0, 0, 0, 3),
      ('service', 'detailUrl',        '详情页地址',       'TEXT',     null,                                                   0, 0, 0, 4),
      ('service', 'detailLabel',      '详情链接文案',     'TEXT',     null,                                                   0, 0, 0, 5),
      ('step',    'body',             '正文',             'TEXTAREA', null,                                                   0, 0, 0, 1),
      ('faq',     'answer',           '答案',             'TEXTAREA', null,                                                   0, 0, 0, 1),
      ('status',  'client',           '客户',             'TEXT',     null,                                                   0, 0, 0, 1),
      ('status',  'startDate',        '上线日期',         'DATE',     null,                                                   0, 0, 0, 2),
      ('status',  'note',             '近况',             'TEXT',     null,                                                   0, 0, 0, 3),
      ('status',  'state',            '状态',             'ENUM',     'ok:正常,warn:注意,down:故障',                            0, 0, 0, 4),
      ('landing', 'lead',             '首屏导语',         'TEXTAREA', null,                                                   0, 0, 0, 1),
      ('landing', 'ogTitle',          '分享标题',         'TEXT',     null,                                                   0, 0, 0, 2),
      ('landing', 'scenesTitle',      '场景区标题',       'TEXT',     null,                                                   0, 0, 0, 3),
      ('landing', 'scenesNote',       '场景区说明',       'TEXT',     null,                                                   0, 0, 0, 4),
      ('landing', 'scenesBody',       '场景正文',         'RICHTEXT', null,                                                   1, 1, 0, 5),
      ('landing', 'stepsTitle',       '流程区标题',       'TEXT',     null,                                                   0, 0, 0, 6),
      ('landing', 'stepsNote',        '流程区说明',       'TEXT',     null,                                                   0, 0, 0, 7),
      ('landing', 'costTitle',        '价格区标题',       'TEXT',     null,                                                   0, 0, 0, 8),
      ('landing', 'costNote',         '价格区说明',       'TEXT',     null,                                                   0, 0, 0, 9),
      ('landing', 'costBody',         '价格正文',         'RICHTEXT', null,                                                   1, 1, 0, 10),
      ('landing', 'deliverTitle',     '交付区标题',       'TEXT',     null,                                                   0, 0, 0, 11),
      ('landing', 'deliverNote',      '交付区说明',       'TEXT',     null,                                                   0, 0, 0, 12),
      ('landing', 'deliverBody',      '交付正文',         'RICHTEXT', null,                                                   1, 1, 0, 13),
      ('landing', 'faqTitle',         '问答区标题',       'TEXT',     null,                                                   0, 0, 0, 14),
      ('landing', 'faqNote',          '问答区说明',       'TEXT',     null,                                                   0, 0, 0, 15),
      ('landing', 'formTopic',        '表单默认咨询主题', 'TEXT',     null,                                                   0, 0, 0, 16)
  ) as v(type_code, code, label, field_type, options, raw, searchable, indexed, sort)
 where s.code = 'lingjiuw' and s.deleted = 0
   and f.site_id = s.id
   and f.type_code = v.type_code
   and f.code = v.code
   and f.deleted = 0;

-- ---------------------------------------------------------------------------
-- 5. 内容
--
-- create_time / update_time 显式写死成 2026-10-01（而不是默认 now()）：它们是
-- 「时间因子」三处一致的取值来源——JSON-LD 的 dateModified、页脚可见的「更新于」、
-- sitemap 的 <lastmod>。写 now() 会让每次迁移的产物日期都不一样。
-- ---------------------------------------------------------------------------

-- 5.1 首页文案（home × 1）：页面 title/seo 走内置列，区块标题走自定义字段
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, status, sort,
                         publish_time, content_format, seo_title, seo_description, data,
                         word_count, view_count, create_time, update_time)
select s.id, 'home', 0, 'index',
       '凌久网 — 小程序开发 · 网站建设 · 定制系统开发',
       '微信/抖音/支付宝小程序开发、网站建设与定制系统开发。免费评估需求，工作日 24 小时内答复。',
       'PUBLISHED', 1,
       timestamp '2026-09-30 09:00:00', 'RICHTEXT',
       '凌久网 — 小程序开发 · 网站建设 · 定制系统开发',
       '凌久网：微信/抖音/支付宝小程序开发、网站建设与定制系统开发。免费评估需求，工作日 24 小时内答复。',
       '{"heroKeywords":"小程序开发 · 网站建设 · 定制系统 · 长期运维",
         "heroLine1":"系统上线只是开始，",
         "heroLine2":"跑得久才算做好。",
         "heroLead":"凌久网是一支做技术交付的团队：小程序、网站、定制系统、数据集成，以及上线之后的长期运维。你说明业务，我们负责技术，按时交付，持续护航。",
         "panelTitle":"一张典型的交付，长这样",
         "panelStatus":"全部正常，监控覆盖中",
         "panelUptimeLabel":"已连续交付运行",
         "servicesTitle":"我们能做的事",
         "servicesNote":"四类服务，覆盖一个系统从想法到长期运行的全部环节。点击任意一项，直接发起咨询。",
         "servicesHint":"需要的服务不在列表里？直接问，大概率我们也做。",
         "processTitle":"合作四步，每一步都说清楚",
         "processNote":"从第一次沟通到长期运维，规则提前讲明白，中途没有意外。",
         "statusTitle":"系统跑得怎么样，直接看",
         "statusNote":"我们交付的每个系统都接入监控告警。与其听承诺，不如看正在运行的系统。",
         "statusSummary":"当前全部运行正常，在管系统 30+，平均故障响应 18 分钟",
         "statusFoot":"以上为运维看板的展示样式，案例信息已脱敏。合作后你可以随时查看自己系统的同款看板。",
         "faqTitle":"你可能想问",
         "faqNote":"合作前把这几件事说清楚，后面都省事。还有别的疑问，直接联系我们。",
         "formTopic":"还没选具体服务，先随便聊聊也可以"}'::jsonb, 0, 0,
       timestamp '2026-09-30 09:00:00', timestamp '2026-10-01 09:00:00'
  from cms_site s
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'home' and c.deleted = 0);

update cms_content c
   set title           = v.title,
       summary         = v.summary,
       seo_title       = v.title,
       seo_description = v.summary,
       status          = 'PUBLISHED',
       publish_time    = timestamp '2026-09-30 09:00:00',
       update_time     = timestamp '2026-10-01 09:00:00',
       data            = v.data::jsonb
  from cms_site s
  cross join (values (
      '凌久网 — 小程序开发 · 网站建设 · 定制系统开发',
      '凌久网：微信/抖音/支付宝小程序开发、网站建设与定制系统开发。免费评估需求，工作日 24 小时内答复。',
      '{"heroKeywords":"小程序开发 · 网站建设 · 定制系统 · 长期运维",
        "heroLine1":"系统上线只是开始，",
        "heroLine2":"跑得久才算做好。",
        "heroLead":"凌久网是一支做技术交付的团队：小程序、网站、定制系统、数据集成，以及上线之后的长期运维。你说明业务，我们负责技术，按时交付，持续护航。",
        "panelTitle":"一张典型的交付，长这样",
        "panelStatus":"全部正常，监控覆盖中",
        "panelUptimeLabel":"已连续交付运行",
        "servicesTitle":"我们能做的事",
        "servicesNote":"四类服务，覆盖一个系统从想法到长期运行的全部环节。点击任意一项，直接发起咨询。",
        "servicesHint":"需要的服务不在列表里？直接问，大概率我们也做。",
        "processTitle":"合作四步，每一步都说清楚",
        "processNote":"从第一次沟通到长期运维，规则提前讲明白，中途没有意外。",
        "statusTitle":"系统跑得怎么样，直接看",
        "statusNote":"我们交付的每个系统都接入监控告警。与其听承诺，不如看正在运行的系统。",
        "statusSummary":"当前全部运行正常，在管系统 30+，平均故障响应 18 分钟",
        "statusFoot":"以上为运维看板的展示样式，案例信息已脱敏。合作后你可以随时查看自己系统的同款看板。",
        "faqTitle":"你可能想问",
        "faqNote":"合作前把这几件事说清楚，后面都省事。还有别的疑问，直接联系我们。",
        "formTopic":"还没选具体服务，先随便聊聊也可以"}'
  )) as v(title, summary, data)
 where s.code = 'lingjiuw' and s.deleted = 0
   and c.site_id = s.id and c.type_code = 'home' and c.deleted = 0;

-- 5.2 服务项（service × 15）
--
-- sort 是**全局** 10…150，不是栏内 1…6：JSON-LD 的 OfferCatalog 按 sort 取一遍，
-- 全局序号才能得到与原站一致的顺序（应用开发 6 项 → 效率与自动化 2 项 → 集成与数据 3 项 →
-- 顾问与运维 4 项）。首页与下拉层分栏时再叠 where='group:eq:…'，栏内仍是这个顺序。
-- 第 4 项要特别注意：显示名是「官方网站」，而 data-topic 是「官方网站建设」，两者都要存。
-- 只有第 1 项有详情落地页（/wechat-miniprogram/），其余 detailUrl / detailLabel 留空。
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, status, sort,
                         publish_time, content_format, data, word_count, view_count,
                         create_time, update_time)
select s.id, 'service', 0, v.slug, v.title, v.summary, 'PUBLISHED', v.sort,
       timestamp '2026-09-30 09:00:00', 'RICHTEXT', v.data::jsonb, 0, 0,
       timestamp '2026-09-30 09:00:00', timestamp '2026-10-01 09:00:00'
  from cms_site s
  cross join (values
      ('wechat-miniprogram', '微信小程序开发', '点餐、预约、商城、会员，按你的业务做，不做套壳模板。', 10,
       '{"group":"dev","icon":"i-wechat","topic":"微信小程序开发","detailUrl":"/wechat-miniprogram/","detailLabel":"微信小程序开发详情"}'),
      ('douyin-miniprogram', '抖音小程序开发', '接住短视频和直播带来的流量，在抖音里完成转化。', 20,
       '{"group":"dev","icon":"i-douyin","topic":"抖音小程序开发"}'),
      ('alipay-miniprogram', '支付宝小程序开发', '面向生活服务与政企场景，打通支付与信用能力。', 30,
       '{"group":"dev","icon":"i-alipay","topic":"支付宝小程序开发"}'),
      ('website', '官方网站', '让客户搜得到、看得懂、联系得上你。', 40,
       '{"group":"dev","icon":"i-site","topic":"官方网站建设"}'),
      ('cms', 'CMS 内容管理系统', '新闻、产品、案例自己更新，不用每次找人改。', 50,
       '{"group":"dev","icon":"i-cms","topic":"CMS 内容管理系统"}'),
      ('custom-system', '定制系统开发', '进销存、CRM、审批流，按你的流程做，而不是让你改流程。', 60,
       '{"group":"dev","icon":"i-custom","topic":"定制系统开发"}'),
      ('automation-script', '内部工具与自动化脚本', '把人肉重复的操作交给脚本，更快，出错更少。', 70,
       '{"group":"auto","icon":"i-script","topic":"内部工具与自动化脚本"}'),
      ('excel-report', 'Excel 与报表自动化', '日报周报自动生成，开会前数据已经躺在你邮箱里。', 80,
       '{"group":"auto","icon":"i-report","topic":"Excel 与报表自动化"}'),
      ('api-integration', 'API 对接', '让系统之间自己说话：订单、库存、物流、支付。', 90,
       '{"group":"data","icon":"i-api","topic":"API 对接"}'),
      ('data-collection', '数据采集', '合规地采集公开数据，清洗整理成能用的表格。', 100,
       '{"group":"data","icon":"i-collect","topic":"数据采集"}'),
      ('system-integration', '系统集成', '新老系统并存不打架，同一份数据只录一次。', 110,
       '{"group":"data","icon":"i-integrate","topic":"系统集成"}'),
      ('tech-review', '技术顾问与架构评审', '动手之前先帮你把方案看一遍，避开昂贵的坑。', 120,
       '{"group":"ops","icon":"i-review","topic":"技术顾问与架构评审"}'),
      ('performance', '性能优化', '页面慢、接口卡，定位到具体那一行。', 130,
       '{"group":"ops","icon":"i-perf","topic":"性能优化"}'),
      ('deploy-ops', '部署运维与云架构', '服务器选型、部署、备份，成本花在刀刃上。', 140,
       '{"group":"ops","icon":"i-deploy","topic":"部署运维与云架构"}'),
      ('monitoring', '监控告警', '系统一出问题我们先知道，而不是你的客户先知道。', 150,
       '{"group":"ops","icon":"i-monitor","topic":"监控告警"}')
  ) as v(slug, title, summary, sort, data)
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'service'
                      and c.slug = v.slug and c.deleted = 0);

-- ---------------------------------------------------------------------------
-- 5.3 服务落地页（landing × 1）
--
-- 每个服务落地页 = 一条内容：URL 由 detail_url_pattern='/{slug}/' 算出（/wechat-miniprogram/），
-- 正文三段富文本与各区块标题都在 data 里。以后要拆「抖音小程序开发」落地页，
-- 照这条再插一行即可，模板不用动。
-- ---------------------------------------------------------------------------
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, status, sort,
                         publish_time, content_format, seo_title, seo_description, data,
                         word_count, view_count, create_time, update_time)
select s.id, 'landing', 0, 'wechat-miniprogram', '微信小程序开发',
       '点餐、预约、商城、会员四类常见场景的微信小程序定制开发，含界面设计、后台管理端与上线部署。',
       'PUBLISHED', 1,
       timestamp '2026-10-01 09:00:00', 'RICHTEXT',
       '微信小程序开发 — 点餐、商城、会员系统定制 | 凌久网',
       '微信小程序开发：点餐、预约、商城、会员四类常见场景，按你的业务流程做，不用套壳模板。报价两万起，标准项目 4–6 周上线，源码与账号全归你。',
       json_build_object(
         'lead', '点餐、预约、商城、会员——四类最常见的场景我们都有成熟做法，但代码是按你的业务流程写的，不是拿模板改个名字。先免费评估需求，合适再谈钱。',
         'ogTitle', '微信小程序开发 — 点餐、商城、会员系统定制',
         'scenesTitle', '微信小程序适合你的哪种生意',
         'scenesNote', '点餐、预约、商城、会员四类场景覆盖了绝大多数需求。对号入座之后，剩下的事情就是把你的流程讲清楚。',
         'scenesBody', '<h3>点餐与门店经营</h3>
      <p>扫码点单、桌台管理、后厨打单、会员储值。适合有实体门店的餐饮：客人不用等服务员，订单直接进后厨，翻台更快，每天的账也不再靠手记。</p>

      <h3>预约与到店服务</h3>
      <p>美业、维修、医美、教培这类靠时间排期的生意：客户自己选时间、选人、付定金，到店核销。爽约率会明显下降，前台也不用再一条条微信对时间。</p>

      <h3>商城与分销</h3>
      <p>商品、订单、优惠券、拼团、分销员。适合已经有私域流量的品牌：交易在微信里闭环，不用把利润分给平台，客户也留在自己手上。</p>

      <h3>会员与内部工具</h3>
      <p>积分、储值、次卡，把线下老客户沉淀成能触达的会员资产；也可以做员工侧的巡检、报修、审批，把原来贴在墙上的表格搬进手机。</p>

      <p>四类都不完全贴合？也没关系。把业务流程说清楚，我们从零做——<a href="#contact">直接说需求</a>就行。想看我们的完整服务范围，可以去<a href="/#services">首页的服务索引</a>。</p>',
         'stepsTitle', '一个小程序从沟通到上线',
         'stepsNote', '规则提前讲明白：每一步交付什么、什么时候交、怎么验收。',
         'costTitle', '要花多少钱、多久能上线',
         'costNote', '先说区间，再说影响区间的东西。最终价格以写进合同的报价单为准。',
         'costBody', '<h3>报价区间</h3>
      <p>标准小程序两万起，包含界面设计、前端开发、后台管理端与上线部署。商城、分销、多门店、多角色后台这类范围更大的，按范围评估后给一份逐项报价。</p>

      <h3>影响价格的四件事</h3>
      <ul>
        <li>页面数量与流程复杂度：一个下单流程走三步还是七步，工作量差一倍。</li>
        <li>是否要微信支付与退款：涉及商户号申请、对账与退款流程，是独立的一块工作量。</li>
        <li>要不要对接你现有的系统：进销存、ERP、第三方接口的可用程度直接决定工期。</li>
        <li>后台有几个角色：门店、员工、分销员各看各的数据，权限体系要单独设计。</li>
      </ul>

      <h3>工期</h3>
      <p>标准小程序 4–6 周。如果还要办小程序备案、申请微信支付商户号，第三方流程会额外占时间——我们会把每一步的时间写进方案，延期怎么处理也提前约定。</p>',
         'deliverTitle', '交付什么、归谁',
         'deliverNote', '这几条是合同里的条款，不是口头承诺。',
         'deliverBody', '<ul>
        <li><strong>源码、文档、账号权限全部归你。</strong>不存在"离了我们就转不动"的设计，想换团队维护，随时可以。</li>
        <li><strong>小程序用你自己的主体注册。</strong>企业或个体工商户资质都是你自己的，账号所有权在你手上，我们只做开发。</li>
        <li><strong>别人做了一半的项目可以接手。</strong>先做一次代码与架构体检，如实告诉你接手的成本和风险，再由你决定是接着做还是重做。</li>
        <li><strong>上线只是中场。</strong>交付的系统接入监控告警，故障按约定时限响应（一般 30 分钟内），小调整顺手处理，不另收费。</li>
      </ul>',
         'faqTitle', '做小程序之前，先看这几条',
         'faqNote', '都是合作前最常被问到的问题。还有别的疑问，直接联系我们。',
         'formTopic', '微信小程序开发'
       )::jsonb, 0, 0,
       timestamp '2026-10-01 09:00:00', timestamp '2026-10-01 09:00:00'
  from cms_site s
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'landing'
                      and c.slug = 'wechat-miniprogram' and c.deleted = 0);

-- 5.4 合作流程（step × 8）
--     首页 4 条是**顶级**（parent_id = 0），落地页 4 条挂在落地页内容下面：
--     模板在首页按 where='parentId:eq:0' 取，在落地页按 of='self' 取，同一段标记两页通用。
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, status, sort,
                         publish_time, content_format, data, word_count, view_count,
                         create_time, update_time)
select s.id, 'step', 0, v.slug, v.title, null, 'PUBLISHED', v.sort,
       timestamp '2026-09-30 09:00:00', 'RICHTEXT', v.data::jsonb, 0, 0,
       timestamp '2026-09-30 09:00:00', timestamp '2026-10-01 09:00:00'
  from cms_site s
  cross join (values
      ('home-1', '聊需求', 1, '{"body":"免费。你说业务目标，我们帮你判断该做什么、不该做什么。"}'),
      ('home-2', '定方案', 2, '{"body":"范围、工期、价格写进合同，中途加需求先谈价再动手。"}'),
      ('home-3', '做开发', 3, '{"body":"每周同步进度，分阶段验收，不是交付那天才第一次见面。"}'),
      ('home-4', '长期护航', 4, '{"body":"上线只是中场。运维期内故障限时响应，小调整顺手就改。"}')
  ) as v(slug, title, sort, data)
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'step'
                      and c.slug = v.slug and c.deleted = 0);

insert into cms_content (site_id, type_code, parent_id, slug, title, summary, status, sort,
                         publish_time, content_format, data, word_count, view_count,
                         create_time, update_time)
select s.id, 'step',
       (select c.id from cms_content c
         where c.site_id = s.id and c.type_code = 'landing'
           and c.slug = 'wechat-miniprogram' and c.deleted = 0),
       v.slug, v.title, null, 'PUBLISHED', v.sort,
       timestamp '2026-10-01 09:00:00', 'RICHTEXT', v.data::jsonb, 0, 0,
       timestamp '2026-10-01 09:00:00', timestamp '2026-10-01 09:00:00'
  from cms_site s
  cross join (values
      ('wechat-1', '聊需求', 1, '{"body":"免费。你说业务目标，我们把流程拆成页面清单，顺便告诉你哪些能砍——这一步常常省下两成预算。"}'),
      ('wechat-2', '定方案与报价', 2, '{"body":"范围、工期、价格写进合同，报价单写清做什么、不做什么。中途加需求先谈价再动手。"}'),
      ('wechat-3', '开发与联调', 3, '{"body":"每周同步进度，分阶段验收。微信支付、短信、地图，以及你现有的进销存或 ERP，都在这一阶段打通。"}'),
      ('wechat-4', '提审与上线', 4, '{"body":"提交微信审核、处理整改意见，上线后接入监控告警。运维期内故障限时响应，小调整顺手就改。"}')
  ) as v(slug, title, sort, data)
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'step'
                      and c.slug = v.slug and c.deleted = 0);

-- 5.5 常见问题（faq × 10）
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, status, sort,
                         publish_time, content_format, data, word_count, view_count,
                         create_time, update_time)
select s.id, 'faq', 0, v.slug, v.title, null, 'PUBLISHED', v.sort,
       timestamp '2026-09-30 09:00:00', 'RICHTEXT', v.data::jsonb, 0, 0,
       timestamp '2026-09-30 09:00:00', timestamp '2026-10-01 09:00:00'
  from cms_site s
  cross join (values
      ('home-1', '做一个项目大概多少钱？', 1,
       '{"answer":"看范围。自动化脚本和报表类通常几千元；小程序和官网一般两万起；定制系统按范围评估。聊完需求我们会出一份写清范围与价格的报价单，不做口头估价。"}'),
      ('home-2', '多久能上线？', 2,
       '{"answer":"简单官网 2–3 周，标准小程序 4–6 周，定制系统视范围而定。方案里会写明每个里程碑的时间，延期怎么处理也会提前约定。"}'),
      ('home-3', '已有系统做了一半，或者是别人做的，能接手吗？', 3,
       '{"answer":"可以。我们会先做一次代码与架构体检，如实告诉你接手的成本和风险，再由你决定是接着做还是重做。"}'),
      ('home-4', '上线之后出问题找谁？', 4,
       '{"answer":"直接找我们，不用排队等工单。运维期内故障按约定时限响应（一般 30 分钟内），小调整顺手处理，不另收费。"}'),
      ('home-5', '源代码和服务器账号归谁？', 5,
       '{"answer":"全部归你。交付完整源码、文档和账号权限，不存在\"离了我们就转不动\"的设计。想换团队维护，随时可以。"}')
  ) as v(slug, title, sort, data)
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'faq'
                      and c.slug = v.slug and c.deleted = 0);

insert into cms_content (site_id, type_code, parent_id, slug, title, summary, status, sort,
                         publish_time, content_format, data, word_count, view_count,
                         create_time, update_time)
select s.id, 'faq',
       (select c.id from cms_content c
         where c.site_id = s.id and c.type_code = 'landing'
           and c.slug = 'wechat-miniprogram' and c.deleted = 0),
       v.slug, v.title, null, 'PUBLISHED', v.sort,
       timestamp '2026-10-01 09:00:00', 'RICHTEXT', v.data::jsonb, 0, 0,
       timestamp '2026-10-01 09:00:00', timestamp '2026-10-01 09:00:00'
  from cms_site s
  cross join (values
      ('wechat-1', '做一个小程序大概多少钱？', 1,
       '{"answer":"标准小程序两万起，包含界面设计、开发、后台管理端与上线部署；商城、分销、多门店这类范围更大的按范围评估。聊完需求会出一份写清范围的报价单，不做口头估价。"}'),
      ('wechat-2', '多久能上线？', 2,
       '{"answer":"标准小程序 4–6 周。如果还要办小程序备案、申请微信支付商户号，第三方流程会额外占时间。方案里会写明每个里程碑的时间，延期怎么处理也会提前约定。"}'),
      ('wechat-3', '一定要有营业执照吗？', 3,
       '{"answer":"大多数经营场景需要。企业或个体工商户主体才能申请微信支付，个人主体能做的小程序功能有限、也收不了款。暂时没有执照的话，先说说业务，我们帮你判断走哪条路。"}'),
      ('wechat-4', '小程序上线前要备案吗？', 4,
       '{"answer":"要。按工信部要求，小程序需完成备案后才能上线。备案用的是你自己的主体和账号，材料我们协助准备，流程也是我们带着走。"}'),
      ('wechat-5', '上线之后出问题找谁？', 5,
       '{"answer":"直接找我们，不用排队等工单。运维期内故障按约定时限响应（一般 30 分钟内），小调整顺手处理，不另收费。"}')
  ) as v(slug, title, sort, data)
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'faq'
                      and c.slug = v.slug and c.deleted = 0);

-- 5.6 运行看板（status × 4）
--     startDate 喂给 .b-uptime 的 data-start，页面上的天数由 main.js 实时算。
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, status, sort,
                         publish_time, content_format, data, word_count, view_count,
                         create_time, update_time)
select s.id, 'status', 0, v.slug, v.title, null, 'PUBLISHED', v.sort,
       timestamp '2026-09-30 09:00:00', 'RICHTEXT', v.data::jsonb, 0, 0,
       timestamp '2026-09-30 09:00:00', timestamp '2026-10-01 09:00:00'
  from cms_site s
  cross join (values
      ('row-1', '小程序点单与会员系统', 1, '{"client":"连锁餐饮企业","startDate":"2022-04-18","note":"近 30 天故障 0 次","state":"ok"}'),
      ('row-2', '生产报表自动化', 2, '{"client":"制造企业","startDate":"2023-02-06","note":"每天 06:00 自动出报表","state":"ok"}'),
      ('row-3', '多平台订单数据集成', 3, '{"client":"电商团队","startDate":"2023-09-21","note":"日均同步订单 3,000+ 条","state":"ok"}'),
      ('row-4', '内部调度工具', 4, '{"client":"物流公司","startDate":"2024-01-15","note":"近 30 天故障 0 次","state":"ok"}')
  ) as v(slug, title, sort, data)
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'status'
                      and c.slug = v.slug and c.deleted = 0);

-- 5.7 文档页（doc × 1）：隐私政策
--     正文是完整的一篇文档（九节），落在 content 列（预渲染源即 content_html，为空时用 content）。
--     注意：正文最后一节「联系我们」里重复了邮箱/电话/微信号——文档是给人读的完整文本，
--     这里刻意不参数化；改了站点配置里的联系方式，这篇文档要同步改（原文也是这样维护的）。
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, status, sort,
                         publish_time, content_format, seo_title, seo_description, content,
                         data, word_count, view_count, create_time, update_time)
select s.id, 'doc', 0, 'privacy', '隐私政策',
       '本站只在咨询表单中收集你主动填写的称呼、联系方式与需求描述，用于回复咨询。',
       'PUBLISHED', 1,
       timestamp '2026-10-01 09:00:00', 'RICHTEXT',
       '隐私政策 — 凌久网',
       '凌久网隐私政策：本站只在咨询表单中收集你主动填写的称呼、联系方式与需求描述，用于回复咨询，不接入第三方统计与广告，不使用 Cookie。',
       '<p>运营本站的主体是佛山市高明区镜知有物网络技术服务中心（个体工商户），本站品牌名「凌久网」，以下称"我们"。本政策说明我们在你访问本站、通过本站提交需求时，如何处理你的个人信息。本站只有一个页面和一个咨询表单，涉及的个人信息很少，下面写的也都是我们实际在做的事。</p>

  <h2>一、我们收集哪些信息</h2>
  <p>只有你主动填写并发送的内容：你在「说说你的需求」表单里填的<strong>称呼、联系方式（微信或电话）、需求描述</strong>。</p>
  <p>本站没有注册、登录、下单功能，不收集身份证号、银行卡号、精确定位这类信息。</p>

  <h2>二、这些信息怎么送到我们这里</h2>
  <p>点「整理成邮件发送」后，是<strong>你的浏览器在你本机</strong>把这些内容整理成一封邮件，交给你的邮件客户端发出，收件地址是 1661675037@qq.com。</p>
  <p>也就是说：本站是纯静态页面，没有后端接收表单，你填写的内容<strong>不经过本站服务器，本站也不会另存一份</strong>。邮件最终保存在我们的邮箱里，同时也存在于你所用邮箱服务商的服务器上。</p>

  <h2>三、我们用它做什么</h2>
  <p>只用于回复你的咨询：了解需求、评估方案、报价，以及后续的合作沟通。不做其他用途。</p>

  <h2>四、我们不会做什么</h2>
  <ul>
    <li>不把你的信息出售、出租或提供给任何第三方；</li>
    <li>不用于短信、电话或邮件的营销推送（你主动咨询的事项除外）；</li>
    <li>不接入第三方统计、广告或埋点脚本，本站不使用 Cookie。</li>
  </ul>
  <p>页面上的样式、字体、图标全部由本站自己提供，不向 Google Fonts 等外部服务发起请求。</p>

  <h2>五、保存多久、怎么删除</h2>
  <p>邮件往来在你与我们沟通期间及合作结束后保留，用于售后与账务核对。你随时可以要求我们删除：用本页末尾任一方式联系我们，我们核实身份后会删除邮箱里的相关往来记录（法律法规要求必须保留的除外）。</p>

  <h2>六、你的权利</h2>
  <p>对我们持有的你的个人信息，你可以要求查询、更正、删除，也可以要求我们不再联系你。通过本页末尾的联系方式提出即可，我们不会为此设置门槛。</p>

  <h2>七、未成年人</h2>
  <p>本站面向企业与个体经营者，不面向未成年人。如果你未满 14 周岁，请在监护人陪同下使用本站，并由监护人代为提交信息。</p>

  <h2>八、本政策的更新</h2>
  <p>本政策如有调整，会直接更新本页内容，并同步修改页面上的「最近一次更新」日期。</p>

  <h2>九、联系我们</h2>
  <ul>
    <li>邮箱：<a href="mailto:1661675037@qq.com">1661675037@qq.com</a></li>
    <li>电话：<a href="tel:+8617677035288">176 7703 5288</a>（工作日 9:00–19:00）</li>
    <li>微信：lingjiuw_cn（加好友请备注"隐私政策"）</li>
  </ul>',
       '{}'::jsonb, 0, 0,
       timestamp '2026-10-01 09:00:00', timestamp '2026-10-01 09:00:00'
  from cms_site s
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_content c
                    where c.site_id = s.id and c.type_code = 'doc'
                      and c.slug = 'privacy' and c.deleted = 0);

-- ---------------------------------------------------------------------------
-- 6. 字段索引（§2.5）
--
-- where / orderby / facet / relate='field:<code>' **只读索引表**，所以每条 service 都要有
-- group 的索引行，缺一行那条服务就"分栏时看不见"。值为 ENUM 的存储值（dev / auto / …），
-- value_key 标量固定 'default'，value_type 写字段类型名。
-- ---------------------------------------------------------------------------
insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               str_value)
select c.site_id, c.id, 'service', 'group', 'default', 'ENUM',
       c.data ->> 'group'
  from cms_content c
  join cms_site s on s.id = c.site_id
 where s.code = 'lingjiuw' and s.deleted = 0
   and c.type_code = 'service' and c.deleted = 0
   and c.data ->> 'group' is not null
   and not exists (select 1 from cms_content_index i
                    where i.content_id = c.id and i.field_code = 'group'
                      and i.value_key = 'default' and i.deleted = 0);

update cms_content_index i
   set site_id    = c.site_id,
       type_code  = 'service',
       value_type = 'ENUM',
       str_value  = c.data ->> 'group'
  from cms_content c
  join cms_site s on s.id = c.site_id
 where s.code = 'lingjiuw' and s.deleted = 0
   and c.type_code = 'service' and c.deleted = 0
   and i.content_id = c.id and i.field_code = 'group'
   and i.value_key = 'default' and i.deleted = 0;

-- ---------------------------------------------------------------------------
-- 7. 导航菜单（§3.4）
--
-- 页头第一项「解决方案」是下拉层触发器（要带 aria-haspopup / #solutionsMega），
-- 它是结构不是普通链接，所以固定在模板里；菜单只装它右边的四个链接。
-- 页脚导航是第二个菜单。锚点一律写 /#xxx：落地页在 /wechat-miniprogram/ 深度上，
-- 写 #xxx 会指向本页那个不存在的锚点。
-- ---------------------------------------------------------------------------
insert into cms_menu (site_id, code, name, status, sort)
select s.id, v.code, v.name, 1, v.sort
  from cms_site s
  cross join (values ('main', '主导航', 1), ('footer', '页脚导航', 2)) as v(code, name, sort)
 where s.code = 'lingjiuw' and s.deleted = 0
   and not exists (select 1 from cms_menu m
                    where m.site_id = s.id and m.code = v.code and m.deleted = 0);

update cms_menu m
   set name   = v.name,
       status = 1,
       sort   = v.sort
  from cms_site s
  cross join (values ('main', '主导航', 1), ('footer', '页脚导航', 2)) as v(code, name, sort)
 where s.code = 'lingjiuw' and s.deleted = 0
   and m.site_id = s.id and m.code = v.code and m.deleted = 0;

insert into cms_menu_item (menu_id, parent_id, label, kind, url, target, rel, visible, sort)
select m.id, 0, v.label, 'url', v.url, null, null, 1, v.sort
  from cms_menu m
  join cms_site s on s.id = m.site_id
  cross join (values
      ('main',   '服务流程', '/#process', 1),
      ('main',   '运行状态', '/#status',  2),
      ('main',   '常见问题', '/#faq',     3),
      ('main',   '联系我们', '/#contact', 4),
      ('footer', '解决方案', '/#services', 1),
      ('footer', '服务流程', '/#process',  2),
      ('footer', '运行状态', '/#status',   3),
      ('footer', '常见问题', '/#faq',      4),
      ('footer', '联系我们', '/#contact',  5)
  ) as v(menu_code, label, url, sort)
 where s.code = 'lingjiuw' and s.deleted = 0
   and m.code = v.menu_code and m.deleted = 0
   and not exists (select 1 from cms_menu_item i
                    where i.menu_id = m.id and i.label = v.label
                      and i.parent_id = 0 and i.deleted = 0);

update cms_menu_item i
   set kind    = 'url',
       url     = v.url,
       visible = 1,
       sort    = v.sort
  from cms_menu m
  join cms_site s on s.id = m.site_id
  cross join (values
      ('main',   '服务流程', '/#process', 1),
      ('main',   '运行状态', '/#status',  2),
      ('main',   '常见问题', '/#faq',     3),
      ('main',   '联系我们', '/#contact', 4),
      ('footer', '解决方案', '/#services', 1),
      ('footer', '服务流程', '/#process',  2),
      ('footer', '运行状态', '/#status',   3),
      ('footer', '常见问题', '/#faq',      4),
      ('footer', '联系我们', '/#contact',  5)
  ) as v(menu_code, label, url, sort)
 where s.code = 'lingjiuw' and s.deleted = 0
   and m.code = v.menu_code and m.deleted = 0
   and i.menu_id = m.id and i.label = v.label and i.parent_id = 0 and i.deleted = 0;

-- ---------------------------------------------------------------------------
-- 8. 自检（不在迁移里跑断言，断言放在发布验证里）
--    迁移执行后应满足：
--      cms_site                     code='lingjiuw' 1 行
--      cms_site_publish_option      该站点 38 条（迁移 11 播的 55 条标准项 + 本次新增项，覆盖后仍为 55+）
--      cms_content_type             7 个
--      cms_field                    46 个（home 19 / service 5 / step 1 / faq 1 / status 4 / landing 16 / doc 0）
--      cms_content                  40 条（home 1 / service 15 / landing 1 / step 8 / faq 10 / status 4 / doc 1）
--      cms_content_index            service.group 15 行
--      cms_menu                     2 个，cms_menu_item 9 条
-- ---------------------------------------------------------------------------
