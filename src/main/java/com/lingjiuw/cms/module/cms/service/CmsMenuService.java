package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.MenuItemSaveRequest;
import com.lingjiuw.cms.module.cms.dto.MenuItemVO;
import com.lingjiuw.cms.module.cms.dto.MenuSaveRequest;
import com.lingjiuw.cms.module.cms.dto.MenuVO;
import com.lingjiuw.cms.module.cms.entity.CmsMenu;
import com.lingjiuw.cms.module.cms.entity.CmsMenuItem;
import com.lingjiuw.cms.module.cms.mapper.CmsMenuMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsMenuItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导航菜单与菜单项（static-publish.md §2.4）。
 *
 * <p>写的是 {@code cms_menu} / {@code cms_menu_item}（站点自己的导航），与平台的 {@code sys_menu}
 * 无关；发布引擎按 {@code {cms:channel source='menu' code='main'}} 读它们。
 *
 * <p>删除都是逻辑删除，并且连带子表：菜单删掉后它的菜单项、菜单项删掉后它的子项都要一起下线，
 * 否则库里留下发布时读不到的孤儿行。
 */
@Service
@RequiredArgsConstructor
public class CmsMenuService {

    /**
     * {@code kind} 的合法取值：与模板标签 {@code ChannelTag.menuNode} 的 switch 分支一一对应，
     * 也与 {@code cms_menu_item.kind} 的列注释一致（§2.4）。
     */
    private static final List<String> KINDS = List.of(
            "category", "content", "url", "type", "tag", "archive", "custom", "author");

    private final CmsMenuMapper menuMapper;
    private final CmsMenuItemMapper menuItemMapper;

    /* ---------------- 菜单 ---------------- */

    /** 当前站点的菜单列表，每个菜单带自己的菜单项树（同层按 sort, id 升序） */
    public List<MenuVO> list() {
        List<CmsMenu> menus = menuMapper.selectList(Wrappers.<CmsMenu>lambdaQuery()
                .eq(CmsMenu::getSiteId, SiteContext.siteId())
                .orderByAsc(CmsMenu::getSort)
                .orderByAsc(CmsMenu::getId));
        if (menus.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = new ArrayList<>(menus.size());
        for (CmsMenu menu : menus) {
            menuIds.add(menu.getId());
        }
        List<CmsMenuItem> items = menuItemMapper.selectList(Wrappers.<CmsMenuItem>lambdaQuery()
                .in(CmsMenuItem::getMenuId, menuIds)
                .orderByAsc(CmsMenuItem::getSort)
                .orderByAsc(CmsMenuItem::getId));
        Map<Long, List<CmsMenuItem>> itemsByMenu = new LinkedHashMap<>();
        for (CmsMenuItem item : items) {
            itemsByMenu.computeIfAbsent(item.getMenuId(), key -> new ArrayList<>()).add(item);
        }
        List<MenuVO> result = new ArrayList<>(menus.size());
        for (CmsMenu menu : menus) {
            MenuVO vo = new MenuVO();
            vo.setId(menu.getId());
            vo.setCode(menu.getCode());
            vo.setName(menu.getName());
            vo.setStatus(menu.getStatus());
            vo.setSort(menu.getSort());
            vo.setItems(tree(itemsByMenu.getOrDefault(menu.getId(), List.of())));
            result.add(vo);
        }
        return result;
    }

    public void create(MenuSaveRequest request) {
        checkCodeUnique(request.code(), null);
        CmsMenu menu = new CmsMenu();
        applyRequest(menu, request);
        menu.setSiteId(SiteContext.siteId());
        menuMapper.insert(menu);
    }

    public void update(Long id, MenuSaveRequest request) {
        CmsMenu menu = requireMenu(id);
        checkCodeUnique(request.code(), id);
        applyRequest(menu, request);
        menuMapper.updateById(menu);
    }

    /** 删菜单连同它的菜单项 */
    @Transactional
    public void delete(Long id) {
        requireMenu(id);
        menuItemMapper.delete(Wrappers.<CmsMenuItem>lambdaQuery().eq(CmsMenuItem::getMenuId, id));
        menuMapper.deleteById(id);
    }

    /* ---------------- 菜单项 ---------------- */

    public void createItem(Long menuId, MenuItemSaveRequest request) {
        requireMenu(menuId);
        checkItem(request, menuId, null);
        CmsMenuItem item = new CmsMenuItem();
        item.setMenuId(menuId);
        applyItem(item, request);
        menuItemMapper.insert(item);
    }

    public void updateItem(Long itemId, MenuItemSaveRequest request) {
        CmsMenuItem item = requireItem(itemId);
        checkItem(request, item.getMenuId(), itemId);
        applyItem(item, request);
        menuItemMapper.updateById(item);
    }

    /** 删菜单项连同它的子项 */
    @Transactional
    public void deleteItem(Long itemId) {
        CmsMenuItem item = requireItem(itemId);
        List<CmsMenuItem> siblings = menuItemMapper.selectList(Wrappers.<CmsMenuItem>lambdaQuery()
                .eq(CmsMenuItem::getMenuId, item.getMenuId()));
        menuItemMapper.delete(Wrappers.<CmsMenuItem>lambdaQuery().in(CmsMenuItem::getId,
                descendantIds(siblings, itemId)));
    }

    /* ---------------- 组装与校验 ---------------- */

    /** 扁平列表 → 一级菜单项（同层保持传入顺序）；父项不在集合里的当一级处理，坏数据也能列出来 */
    private static List<MenuItemVO> tree(List<CmsMenuItem> items) {
        Map<Long, MenuItemVO> nodes = new LinkedHashMap<>();
        for (CmsMenuItem item : items) {
            MenuItemVO node = new MenuItemVO();
            node.setId(item.getId());
            node.setParentId(item.getParentId());
            node.setLabel(item.getLabel());
            node.setKind(item.getKind());
            node.setRefId(item.getRefId());
            node.setRefCode(item.getRefCode());
            node.setUrl(item.getUrl());
            node.setTarget(item.getTarget());
            node.setRel(item.getRel());
            node.setVisible(item.getVisible());
            node.setSort(item.getSort());
            nodes.put(item.getId(), node);
        }
        List<MenuItemVO> roots = new ArrayList<>();
        for (MenuItemVO node : nodes.values()) {
            MenuItemVO parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /** 该项 + 全部后代的 id（坏数据里的环不会让它无限循环） */
    private static List<Long> descendantIds(List<CmsMenuItem> all, Long rootId) {
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        pending.add(rootId);
        while (!pending.isEmpty()) {
            Long id = pending.poll();
            if (!seen.add(id)) {
                continue;
            }
            ids.add(id);
            for (CmsMenuItem row : all) {
                if (id.equals(row.getParentId())) {
                    pending.add(row.getId());
                }
            }
        }
        return ids;
    }

    /** kind 合法 + 按 kind 必填的字段 + 上级项属于同一个菜单（§2.4） */
    private void checkItem(MenuItemSaveRequest request, Long menuId, Long selfId) {
        String kind = request.kind() == null ? "" : request.kind().trim();
        if (!KINDS.contains(kind)) {
            throw new BizException("菜单项类型只能是 " + String.join("、", KINDS) + "：" + request.kind());
        }
        switch (kind) {
            case "url" -> {
                if (!StringUtils.hasText(request.url())) {
                    throw new BizException("外链类型的菜单项必须填链接地址");
                }
            }
            case "type", "tag", "archive" -> {
                if (!StringUtils.hasText(request.refCode())) {
                    throw new BizException("指向内容类型 / 标签 / 归档的菜单项必须填 refCode");
                }
            }
            case "category", "content" -> {
                if (request.refId() == null || request.refId() <= 0) {
                    throw new BizException("指向分类 / 内容的菜单项必须填 refId");
                }
            }
            default -> {
                // custom（纯占位父项）/ author 不额外要求字段
            }
        }
        checkParent(request.parentId(), menuId, selfId);
    }

    /** 上级项必须是同一个菜单下的菜单项，且不能是自身或自身的下级；0 表示一级项 */
    private void checkParent(Long parentId, Long menuId, Long selfId) {
        if (parentId == null || parentId <= 0) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BizException("上级菜单项不能选择自身");
        }
        CmsMenuItem parent = menuItemMapper.selectById(parentId);
        if (parent == null || !menuId.equals(parent.getMenuId())) {
            throw new BizException("上级菜单项不存在或不属于该菜单");
        }
        if (selfId != null && descendantIds(menuItemMapper.selectList(
                Wrappers.<CmsMenuItem>lambdaQuery().eq(CmsMenuItem::getMenuId, menuId)), selfId)
                .contains(parentId)) {
            // A↔B 互指会让这两项都挂不到 roots 上，整条子树从 list() 里静默消失
            throw new BizException("上级菜单项不能选择自己的下级");
        }
    }

    private void applyRequest(CmsMenu menu, MenuSaveRequest request) {
        menu.setCode(request.code().trim());
        menu.setName(request.name().trim());
        menu.setStatus(request.status() == null ? 1 : request.status());
        menu.setSort(request.sort() == null ? 0 : request.sort());
    }

    private static void applyItem(CmsMenuItem item, MenuItemSaveRequest request) {
        // 非正数一律归一为 0：库里只有"0 = 一级项"这一种约定，负值脏数据会让后续按 parentId 的关联出错
        item.setParentId(request.parentId() == null || request.parentId() <= 0 ? 0L : request.parentId());
        item.setLabel(blankToNull(request.label()));
        item.setKind(request.kind().trim());
        item.setRefId(request.refId());
        item.setRefCode(blankToNull(request.refCode()));
        item.setUrl(blankToNull(request.url()));
        item.setTarget(blankToNull(request.target()));
        item.setRel(blankToNull(request.rel()));
        item.setVisible(request.visible() == null ? 1 : request.visible());
        item.setSort(request.sort() == null ? 0 : request.sort());
    }

    /** 菜单 code 在站点内唯一（库里有部分唯一索引，这里先给出中文提示） */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = menuMapper.selectCount(Wrappers.<CmsMenu>lambdaQuery()
                .eq(CmsMenu::getSiteId, SiteContext.siteId())
                .eq(CmsMenu::getCode, code)
                .ne(excludeId != null, CmsMenu::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("菜单标识已存在：" + code);
        }
    }

    /** 取当前站点下的菜单：别的站点的 id 在这里就当不存在 */
    private CmsMenu requireMenu(Long id) {
        CmsMenu menu = menuMapper.selectOne(Wrappers.<CmsMenu>lambdaQuery()
                .eq(CmsMenu::getId, id)
                .eq(CmsMenu::getSiteId, SiteContext.siteId()));
        if (menu == null) {
            throw new BizException("菜单不存在或已被删除");
        }
        return menu;
    }

    /** 取当前站点下的菜单项：先查菜单归属，别的站点的菜单项在这里就当不存在 */
    private CmsMenuItem requireItem(Long id) {
        CmsMenuItem item = menuItemMapper.selectById(id);
        if (item == null) {
            throw new BizException("菜单项不存在或已被删除");
        }
        requireMenu(item.getMenuId());
        return item;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
