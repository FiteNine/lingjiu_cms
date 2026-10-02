package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.security.LoginUser;
import com.lingjiuw.cms.common.security.SecurityUtils;
import com.lingjiuw.cms.common.site.SitePathBoundary;
import com.lingjiuw.cms.module.cms.dto.DirListing;
import com.lingjiuw.cms.module.cms.dto.SiteOption;
import com.lingjiuw.cms.module.cms.dto.SiteSaveRequest;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContent;
import com.lingjiuw.cms.module.cms.entity.CmsMedia;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.entity.CmsTag;
import com.lingjiuw.cms.module.cms.mapper.CmsCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMediaMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsTagMapper;
import com.lingjiuw.cms.module.system.mapper.SysUserSiteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 站点配置。站点的网站文件目录统一放在 {@code cms.site.root-dir} 之下：
 * 库里只存相对路径，后台的目录浏览与新建都被限制在这个根目录内。
 */
@Service
@RequiredArgsConstructor
public class SiteService {

    /**
     * 站点目录下必须有的三个子目录：data 存站点静态资源、template 存站点模板、www 存发布产物。
     * 建站时三个都建出来；{@code www/} 里的内容只归发布引擎，人工不要往里放东西（E6001）。
     */
    private static final List<String> SITE_SUB_DIRS = List.of("data", "template", "www");

    /** 站点协议与主语言的默认值：这两列 not null，新建站点没填时按默认写（§2.7） */
    private static final String DEFAULT_PROTOCOL = "https";
    private static final String DEFAULT_LANG = "zh-CN";

    private final CmsSiteMapper siteMapper;
    private final CmsContentMapper contentMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsTagMapper tagMapper;
    private final CmsMediaMapper mediaMapper;
    private final SysUserSiteMapper userSiteMapper;
    private final SiteBootstrapService siteBootstrapService;

    @Value("${cms.site.root-dir}")
    private String siteRootDir;

    public List<CmsSite> list() {
        // 与 options() 同一把尺子：只列当前用户能访问的站点（admin 不受绑定限制）
        List<CmsSite> sites = siteMapper.selectList(Wrappers.<CmsSite>lambdaQuery()
                .in(CmsSite::getId, accessibleSiteIds())
                .orderByAsc(CmsSite::getId));
        Path root = siteRoot();
        sites.forEach(site -> site.setRootDirPath(root.resolve(site.getRootDir()).normalize().toString()));
        return sites;
    }

    /** 右上角站点切换器的下拉选项：只返回当前用户可访问的启用站点 */
    public List<SiteOption> options() {
        return siteMapper.selectList(Wrappers.<CmsSite>lambdaQuery()
                        .eq(CmsSite::getStatus, 1)
                        .in(CmsSite::getId, accessibleSiteIds())
                        .orderByAsc(CmsSite::getId)).stream()
                .map(site -> new SiteOption(site.getId(), site.getName(), site.getCode(), site.getIsDefault()))
                .toList();
    }

    /**
     * 把请求里指定的站点解析成一个确定存在的站点 id：没指定、或指定的站点不在
     * {@link #accessibleSiteIds()} 里（无权访问、已被删），都落到默认站点；默认站点也不可访问时
     * 落到第一个可访问站点。见 {@code common/site/SiteInterceptor}。
     */
    public Long resolveSiteId(Long requested) {
        List<Long> accessible = accessibleSiteIds();
        if (requested != null && accessible.contains(requested)) {
            return requested;
        }
        Long defaultId = defaultSiteId();
        return accessible.contains(defaultId) ? defaultId : accessible.get(0);
    }

    /**
     * 公开接口的严格解析：显式传了站点 id 就必须有效且可访问，否则报错；没传（null）才回落默认站点。
     * 后台接口仍走 {@link #resolveSiteId(Long)} 的宽松回落——切到的站点被删或收回授权后后台不能卡死。
     */
    public Long resolveSiteIdStrict(Long requested) {
        if (requested == null) {
            return resolveSiteId(null);
        }
        if (!accessibleSiteIds().contains(requested)) {
            throw new BizException("站点不存在或无权访问");
        }
        return requested;
    }

    /**
     * 当前用户可切换访问的站点 id：
     * <ul>
     *   <li>admin 角色不受绑定限制，全部站点都算；</li>
     *   <li>其余用户取 {@code sys_user_site} 里绑定到的站点；</li>
     *   <li>一个都没绑定（或绑定的站点都已被删）只给默认站点——任何登录用户至少有这一个，</li>
     * </ul>
     * 后台不会因为漏绑定而没有站点可用。未登录的请求（登录接口、公开内容接口）不设限。
     */
    private List<Long> accessibleSiteIds() {
        LoginUser user = SecurityUtils.userOrNull();
        if (user == null || isSuperAdmin(user)) {
            return siteMapper.selectList(Wrappers.<CmsSite>lambdaQuery().select(CmsSite::getId)).stream()
                    .map(CmsSite::getId)
                    .toList();
        }
        List<Long> bound = userSiteMapper.selectSiteIdsByUserId(user.getId());
        if (!bound.isEmpty()) {
            List<Long> existing = siteMapper.selectList(Wrappers.<CmsSite>lambdaQuery()
                            .select(CmsSite::getId)
                            .in(CmsSite::getId, bound)
                            .orderByAsc(CmsSite::getId)).stream()
                    .map(CmsSite::getId)
                    .toList();
            if (!existing.isEmpty()) {
                return existing;
            }
        }
        return List.of(defaultSiteId());
    }

    /** admin 角色放行全部，与前端 userStore.hasPerm 的判断保持一致 */
    private static boolean isSuperAdmin(LoginUser user) {
        return user.getRoles() != null && user.getRoles().contains("admin");
    }

    /** 系统默认站点：迁移保证有且仅有一个 */
    public Long defaultSiteId() {
        CmsSite site = siteMapper.selectDefaultSite();
        if (site == null) {
            throw new BizException("系统缺少默认站点，请到「站点管理」检查站点配置");
        }
        return site.getId();
    }

    /** 站点与它的播种数据（内置类型 / 菜单 / 发布选项）必须一起成功或一起回滚 */
    @Transactional
    public void create(SiteSaveRequest request) {
        checkCodeUnique(request.code(), null);
        CmsSite site = new CmsSite();
        applyRequest(site, request);
        site.setRootDir(prepareSiteDir(request.rootDir()));
        try {
            siteMapper.insert(site);
        } catch (DuplicateKeyException e) {
            // 唯一索引是最终保证：checkCodeUnique 只兜 code，root_dir 撞车（uk_cms_site_root_dir）在这里兜成业务提示
            throw new BizException("该站点目录已被其它站点占用，请更换站点目录");
        }
        // 入库后才有站点 id：内置内容类型、默认菜单与默认发布选项按站点补齐
        siteBootstrapService.seed(site.getId());
    }

    public void update(Long id, SiteSaveRequest request) {
        CmsSite site = requireSite(id);
        checkCodeUnique(request.code(), id);
        applyRequest(site, request);
        site.setRootDir(prepareSiteDir(request.rootDir()));
        try {
            siteMapper.updateById(site);
        } catch (DuplicateKeyException e) {
            // 同上：编辑时把目录改成别人已用的目录也会撞唯一索引，兜成业务提示而不是 500
            throw new BizException("该站点目录已被其它站点占用，请更换站点目录");
        }
    }

    /** 站点与它名下的从属数据（用户绑定、播种的内置类型 / 菜单 / 发布选项）在同一个事务里删干净 */
    @Transactional
    public void delete(Long id) {
        CmsSite site = requireSite(id);
        if (site.getIsDefault() != null && site.getIsDefault() == 1) {
            throw new BizException("默认站点不能删除");
        }
        // 内容都挂着 site_id，站点删掉后这些内容就再也认不回来了，先拦住
        if (hasContent(id)) {
            throw new BizException("该站点下还有内容（内容/分类/标签/媒体），请先清理后再删除");
        }
        // 只删配置：磁盘上的网站文件原样保留，避免误删
        siteMapper.deleteById(id);
        // 绑定跟着站点走，否则留下指向已删站点的脏数据
        userSiteMapper.deleteBySiteId(id);
        // 建站时播种的站点从属数据同样要清掉，否则全是认不回来的孤儿行
        siteBootstrapService.removeSeeded(id);
    }

    private boolean hasContent(Long siteId) {
        return contentMapper.exists(Wrappers.<CmsContent>lambdaQuery().eq(CmsContent::getSiteId, siteId))
                || categoryMapper.exists(Wrappers.<CmsCategory>lambdaQuery().eq(CmsCategory::getSiteId, siteId))
                || tagMapper.exists(Wrappers.<CmsTag>lambdaQuery().eq(CmsTag::getSiteId, siteId))
                || mediaMapper.exists(Wrappers.<CmsMedia>lambdaQuery().eq(CmsMedia::getSiteId, siteId));
    }

    /* ---------------- 站点目录浏览 / 新建 ---------------- */

    /** 列出站点根目录下某个路径的直接子目录 */
    public DirListing listDirs(String path) {
        Path root = siteRoot();
        Path target = SitePathBoundary.resolveUnder(root, path);
        // 路径中间的符号链接不跟随：否则列目录会翻到站点根目录之外
        SitePathBoundary.assertNoSymlink(root, target);
        if (!Files.isDirectory(target)) {
            throw new BizException("目录不存在：" + SitePathBoundary.relative(root, target));
        }
        List<DirListing.DirNode> dirs;
        try (Stream<Path> stream = Files.list(target)) {
            dirs = stream
                    .filter(item -> Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS))
                    .map(item -> new DirListing.DirNode(item.getFileName().toString(), SitePathBoundary.relative(root, item)))
                    .sorted(Comparator.comparing(DirListing.DirNode::name))
                    .toList();
        } catch (IOException e) {
            throw new BizException("读取目录失败：" + e.getMessage());
        }
        return new DirListing(SitePathBoundary.relative(root, target), dirs);
    }

    /** 在站点根目录下新建一个文件夹 */
    public void createDir(String parent, String name) {
        Path root = siteRoot();
        String folder = SitePathBoundary.checkName(name, "文件夹");
        Path parentDir = SitePathBoundary.resolveUnder(root, parent);
        if (!Files.isDirectory(parentDir)) {
            throw new BizException("上级目录不存在，请刷新后重试");
        }
        String parentPath = SitePathBoundary.relative(root, parentDir);
        Path target = SitePathBoundary.resolveUnder(root, parentPath.isEmpty() ? folder : parentPath + "/" + folder);
        // 落盘前逐段查符号链接：上级目录是软链时 createDirectory 会建到站点根目录之外
        SitePathBoundary.assertNoSymlink(root, target);
        try {
            Files.createDirectory(target);
        } catch (FileAlreadyExistsException e) {
            throw new BizException("文件夹已存在：" + folder);
        } catch (IOException e) {
            throw new BizException("新建文件夹失败：" + e.getMessage());
        }
    }

    /**
     * 指定站点的网站文件目录：cms.site.root-dir 下该站点的 root_dir（只解析路径，不创建）。
     * 发布引擎在别的包也要拿得到，所以是 {@code public}。
     */
    public Path siteDir(Long siteId) {
        return siteRoot().resolve(requireSite(siteId).getRootDir()).normalize();
    }

    /** 校验站点目录落在根目录之内，不存在就建出来，并返回入库用的相对路径 */
    private String prepareSiteDir(String relativePath) {
        Path root = siteRoot();
        Path target = SitePathBoundary.resolveUnder(root, relativePath);
        if (target.equals(root)) {
            throw new BizException("站点目录不能是站点根目录本身，请在它下面选择或新建一个文件夹");
        }
        // 落盘前逐段查符号链接：站点根目录内的软链会让 createDirectories 建到根目录之外
        SitePathBoundary.assertNoSymlink(root, target);
        if (Files.exists(target) && !Files.isDirectory(target)) {
            throw new BizException("该路径已被同名文件占用：" + SitePathBoundary.relative(root, target));
        }
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new BizException("创建站点目录失败：" + e.getMessage());
        }
        if (!Files.isWritable(target)) {
            throw new BizException("站点目录不可写：" + SitePathBoundary.relative(root, target));
        }
        String siteDir = SitePathBoundary.relative(root, target);
        // 站点目录下固定要有 data（静态资源）、template（站点模板）与 www（发布产物）：已存在就跳过，缺了就补上
        for (String name : SITE_SUB_DIRS) {
            try {
                Files.createDirectories(target.resolve(name));
            } catch (FileAlreadyExistsException e) {
                throw new BizException("站点目录下有同名文件，无法创建子目录：" + siteDir + "/" + name);
            } catch (IOException e) {
                throw new BizException("创建站点子目录失败：" + siteDir + "/" + name + "（" + e.getMessage() + "）");
            }
        }
        return siteDir;
    }

    /* ---------------- 目录工具 ---------------- */

    /**
     * 站点根目录（cms.site.root-dir）：配置项可以是相对路径，这里统一转成绝对路径，不存在就建出来。
     * 发布引擎在别的包也要拿得到，所以是 {@code public}。
     */
    public Path siteRoot() {
        Path root = Paths.get(siteRootDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BizException("站点根目录不可用：" + root + "（" + e.getMessage() + "）");
        }
        return root;
    }

    /** 站点目录下固定存在的子目录清单（data / template / www），引擎按它补齐目录 */
    public static List<String> subDirs() {
        return SITE_SUB_DIRS;
    }

    /* ---------------- 站点配置 ---------------- */

    private void applyRequest(CmsSite site, SiteSaveRequest request) {
        site.setName(request.name().trim());
        site.setCode(request.code().trim());
        site.setDomain(blankToNull(request.domain()));
        site.setLogo(blankToNull(request.logo()));
        site.setDescription(blankToNull(request.description()));
        site.setKeywords(blankToNull(request.keywords()));
        site.setSeoDescription(blankToNull(request.seoDescription()));
        site.setIcp(blankToNull(request.icp()));
        site.setContactPhone(blankToNull(request.contactPhone()));
        site.setContactEmail(blankToNull(request.contactEmail()));
        site.setProtocol(blankTo(request.protocol(), DEFAULT_PROTOCOL));
        site.setLang(blankTo(request.lang(), DEFAULT_LANG));
        site.setTheme(blankToNull(request.theme()));
        site.setDefaultCover(blankToNull(request.defaultCover()));
        site.setOgImage(blankToNull(request.ogImage()));
        site.setStatisticsCode(blankToNull(request.statisticsCode()));
        site.setStatus(request.status() == null ? 1 : request.status());
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = siteMapper.exists(Wrappers.<CmsSite>lambdaQuery()
                .eq(CmsSite::getCode, code)
                .ne(excludeId != null, CmsSite::getId, excludeId));
        if (exists) {
            throw new BizException("站点标识已存在：" + code);
        }
    }

    private CmsSite requireSite(Long id) {
        CmsSite site = siteMapper.selectById(id);
        if (site == null) {
            throw new BizException("站点不存在或已被删除");
        }
        return site;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 留空时用默认值：protocol / lang 是 not null 列，不能写 null */
    private static String blankTo(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
