package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.PublishOptionSaveRequest;
import com.lingjiuw.cms.module.cms.dto.PublishOptionVO;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.entity.CmsSitePublishOption;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsSitePublishOptionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link PublishOptionService} 删除路径的真库验收（PostgreSQL）。
 *
 * <p><b>为什么要真库</b>：删除靠两处数据库行为撑着——{@code @TableLogic} 把 delete 变成
 * {@code update ... set deleted = 1}，以及部分唯一索引 {@code (site_id, option_code) where deleted = 0}
 * 允许删掉之后再新增同名项。后台页面对用户承诺的正是后者（"需要时用新增选项补回来"），
 * 内存替身证明不了它。因此沿用 {@code ContentServiceTest} 的做法：{@code @SpringBootTest}
 * （不起 Web 容器）+ {@code @Transactional}（结束回滚，不动开发库）+ 每个用例一个独立站点。
 */
@EnabledIf("databaseReachable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=false", "logging.level.root=warn"})
@Transactional
class PublishOptionServiceTest {

    private static final String URL = "jdbc:postgresql://localhost:5432/lingjiuw_cms";
    private static final String USER = "cms";
    private static final String PASSWORD = "cms123456";

    /** 数据库连不上就整类跳过（application.yml 指向的就是这个库）。 */
    static boolean databaseReachable() {
        try (Connection ignored = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired
    private PublishOptionService publishOptionService;
    @Autowired
    private CmsSitePublishOptionMapper optionMapper;
    @Autowired
    private CmsSiteMapper siteMapper;

    private long siteId;

    @BeforeEach
    void setUp() {
        siteId = insertSite("option-test-" + UUID.randomUUID());
        SiteContext.set(siteId);
    }

    @AfterEach
    void tearDown() {
        SiteContext.clear();
    }

    @Test
    void 删除后选项从列表消失且能重新新增同名项() {
        insertOption(siteId, "contact.wechat", "wechat-a");
        assertEquals("wechat-a", valueOf("contact.wechat"));

        publishOptionService.delete("contact.wechat");

        assertFalse(codes().contains("contact.wechat"), "删掉后后台表单里不再有这一行");
        // 页面上的承诺：需要时用「新增选项」补回同名选项。重插能过才算兑现
        publishOptionService.save(new PublishOptionSaveRequest(
                List.of(new PublishOptionSaveRequest.Item("contact.wechat", "wechat-b"))));
        assertEquals("wechat-b", valueOf("contact.wechat"));
    }

    @Test
    void 删除不存在的选项报业务错误() {
        assertThrows(BizException.class, () -> publishOptionService.delete("contact.nope"));
        assertThrows(BizException.class, () -> publishOptionService.delete("   "));
    }

    @Test
    void 删除只作用于当前站点() {
        long otherSiteId = insertSite("option-test-other-" + UUID.randomUUID());
        insertOption(siteId, "contact.phone", "111");
        insertOption(otherSiteId, "contact.phone", "222");

        publishOptionService.delete("contact.phone");

        assertFalse(codes().contains("contact.phone"), "当前站点的这一条已删掉");
        CmsSitePublishOption other = optionMapper.selectOne(Wrappers.<CmsSitePublishOption>lambdaQuery()
                .eq(CmsSitePublishOption::getSiteId, otherSiteId)
                .eq(CmsSitePublishOption::getOptionCode, "contact.phone"));
        assertNotNull(other, "别的站点的同名选项不能跟着被删");
        assertEquals("222", other.getValue());
    }

    /* ---------------- fixture ---------------- */

    private List<String> codes() {
        return publishOptionService.list().stream().map(PublishOptionVO::optionCode).toList();
    }

    private String valueOf(String optionCode) {
        return publishOptionService.list().stream()
                .filter(row -> row.optionCode().equals(optionCode))
                .map(PublishOptionVO::value)
                .findFirst().orElse(null);
    }

    private long insertSite(String code) {
        CmsSite site = new CmsSite();
        site.setName("发布选项测试站");
        site.setCode(code);
        // root_dir 上有唯一索引 uk_cms_site_root_dir（where deleted = 0）：setUp 与「删除只作用于当前站点」
        // 会在同一个事务里各插一个站点，写死同一个目录名会当场撞索引，所以跟着 code 一起唯一
        site.setRootDir(code);
        site.setStatus(1);
        site.setIsDefault(0);
        siteMapper.insert(site);
        return site.getId();
    }

    private void insertOption(long site, String optionCode, String value) {
        CmsSitePublishOption row = new CmsSitePublishOption();
        row.setSiteId(site);
        row.setOptionCode(optionCode);
        row.setValue(value);
        optionMapper.insert(row);
    }
}
