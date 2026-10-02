-- =============================================================================
-- 演示站点：书籍的"连载状态"字段改名 status → bookStatus
-- 契约：docs/static-publish.md §2.2（内置字段与自定义字段的命名）、§5.2.1（formatter 合法性）
--
-- 为什么必须改名：`status` 是 cms_content 上的**内置列**（DRAFT / PUBLISHED / OFFLINE，
-- 见 §2.2 的内置字段表）。类型再定义一个同名的自定义字段，`[field:status/]` 的解析会先命中
-- 内置列（TEXT），于是模板里正确写的 `[field:status format='label']`（ENUM 用法）在编译期
-- 被拒：
--     [E1002] formatter label 不能用在 TEXT 字段上 · 字段 status
--        → 字段类型 TEXT 可用的 formatter 有：maxlen mask upper lower
-- 这是"自定义字段 code 与内置字段重名"这一类问题的实例；改名是唯一无歧义的修法
-- （继续用 status 就得让引擎在重名时偏向自定义字段，而内置 status 的语义又没有别处可放）。
--
-- 同时把模板与索引表里对应的取值一并搬过去，保持三处一致（cms_field / cms_content_index / data）。
-- 时间戳 20261004090003 > 20261004090002（Flyway 未开 out-of-order，版本必须递增）。
-- =============================================================================

-- 1. 字段定义改名（唯一键是 (site_id, type_code, code)，先删旧再插新，幂等）
update cms_field f
   set code  = 'bookStatus',
       label = '连载状态'
  from cms_site s
 where s.id = f.site_id and s.code = 'demo' and s.deleted = 0
   and f.deleted = 0
   and f.type_code = 'book'
   and f.code = 'status'
   and not exists (select 1 from cms_field other
                    where other.site_id = f.site_id and other.type_code = f.type_code
                      and other.code = 'bookStatus' and other.deleted = 0);

-- 2. 字段索引表的列名跟着走（where / orderby 的字段 code 必须能在索引表里对上）
update cms_content_index i
   set field_code = 'bookStatus'
  from cms_site s
 where s.id = i.site_id and s.code = 'demo'
   and i.type_code = 'book'
   and i.field_code = 'status';

-- 3. 内容上的 jsonb 键也跟着走（模板读的是 data 里的键）
update cms_content c
   set data = (c.data - 'status') || jsonb_build_object('bookStatus', c.data -> 'status')
  from cms_site s
 where s.id = c.site_id and s.code = 'demo' and s.deleted = 0
   and c.type_code = 'book'
   and c.data ? 'status'
   and not (c.data ? 'bookStatus');
