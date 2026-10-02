package com.lingjiuw.cms.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.system.dto.MenuNode;
import com.lingjiuw.cms.module.system.dto.MenuSaveRequest;
import com.lingjiuw.cms.module.system.entity.SysMenu;
import com.lingjiuw.cms.module.system.mapper.SysMenuMapper;
import com.lingjiuw.cms.module.system.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public List<MenuNode> tree() {
        List<SysMenu> menus = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
        return buildTree(menus);
    }

    public List<MenuNode> list() {
        return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                        .orderByAsc(SysMenu::getSort)
                        .orderByAsc(SysMenu::getId))
                .stream()
                .map(this::toNode)
                .toList();
    }

    @Transactional
    public void create(MenuSaveRequest request) {
        checkParent(request.parentId());
        SysMenu menu = new SysMenu();
        applyRequest(menu, request);
        menuMapper.insert(menu);
    }

    @Transactional
    public void update(Long id, MenuSaveRequest request) {
        SysMenu menu = requireMenu(id);
        if (request.parentId() != null && request.parentId().equals(id)) {
            throw new BizException("父级菜单不能选择自身");
        }
        checkParent(request.parentId());
        checkParentNotDescendant(id, request.parentId());
        applyRequest(menu, request);
        menuMapper.updateById(menu);
    }

    @Transactional
    public void delete(Long id) {
        requireMenu(id);
        Long children = menuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException("存在子菜单，不能删除");
        }
        menuMapper.deleteById(id);
        roleMenuMapper.deleteByMenuId(id);
    }

    private void applyRequest(SysMenu menu, MenuSaveRequest request) {
        menu.setParentId(request.parentId() == null ? 0L : request.parentId());
        menu.setName(request.name());
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setIcon(request.icon());
        menu.setPerms(request.perms());
        menu.setType(request.type());
        menu.setSort(request.sort() == null ? 0 : request.sort());
        menu.setVisible(request.visible() == null ? 1 : request.visible());
        menu.setStatus(request.status() == null ? 1 : request.status());
        menu.setRemark(request.remark());
    }

    private void checkParent(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (menuMapper.selectById(parentId) == null) {
            throw new BizException("父级菜单不存在");
        }
    }

    /** 新父级不能落在 id 的子树内，否则形成祖先环，环内菜单在树上会丢失 */
    private void checkParentNotDescendant(Long id, Long parentId) {
        Long current = parentId;
        while (current != null && current != 0L) {
            if (current.equals(id)) {
                throw new BizException("父级菜单不能选择自身或其子菜单");
            }
            SysMenu parent = menuMapper.selectById(current);
            if (parent == null) {
                return;
            }
            current = parent.getParentId();
        }
    }

    private SysMenu requireMenu(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException("菜单不存在或已被删除");
        }
        return menu;
    }

    private List<MenuNode> buildTree(List<SysMenu> menus) {
        Map<Long, MenuNode> nodes = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            nodes.put(menu.getId(), toNode(menu));
        }
        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : nodes.values()) {
            MenuNode parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    private MenuNode toNode(SysMenu menu) {
        MenuNode node = new MenuNode();
        BeanUtils.copyProperties(menu, node);
        return node;
    }
}
