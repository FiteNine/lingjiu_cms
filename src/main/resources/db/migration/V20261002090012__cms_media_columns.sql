-- =============================================================================
-- cms_media 补 §7.7 需要的元数据列
-- 契约：docs/static-publish.md §7.7、§12.1 第 12 条
--
-- [field:cover.alt/] / .width / .height 与三档派生尺寸（thumb/medium/large）
-- 全靠这几列；现有的 MediaService 不写它们，需要在上传入库时补上。
-- =============================================================================

alter table cms_media add column alt            varchar(255);   -- 无障碍文本，为空时回退到内容 title
alter table cms_media add column width          int;            -- 原图宽（[field:cover.width/]）
alter table cms_media add column height         int;            -- 原图高
alter table cms_media add column derive_status  varchar(16);    -- PENDING / DONE / FAILED

comment on column cms_media.alt           is '无障碍/SEO 文本；为空时模板回退到当前内容的 title';
comment on column cms_media.width         is '原图宽（像素），上传时由图像库读出';
comment on column cms_media.height        is '原图高（像素），上传时由图像库读出';
comment on column cms_media.derive_status is '派生尺寸生成状态：PENDING 待生成 / DONE 已生成 / FAILED 重试 3 次仍失败（后台标红）';
