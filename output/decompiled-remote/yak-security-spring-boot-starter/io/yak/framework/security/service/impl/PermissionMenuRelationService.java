/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  org.springframework.stereotype.Service
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.MenuPO;
import io.yak.framework.security.common.po.PermissionPO;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import io.yak.framework.security.dao.mapper.MenuMapper;
import io.yak.framework.security.dao.mapper.PermissionMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityPermissionMenuRelationService")
public class PermissionMenuRelationService {
    public static final String NODE_TYPE_ROOT = "ROOT";
    public static final String NODE_TYPE_MENU_GROUP = "MENU_GROUP";
    public static final String NODE_TYPE_MENU = "MENU";
    public static final String NODE_TYPE_PERMISSION_GROUP = "PERMISSION_GROUP";
    public static final String NODE_TYPE_ACTION = "ACTION";
    private static final String MENU_PERMISSION_PREFIX = "menu:";
    private final PermissionMapper permissionMapper;
    private final MenuMapper menuMapper;

    public PermissionMenuRelationService(PermissionMapper permissionMapper, MenuMapper menuMapper) {
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
    }

    public List<Long> inferMenuIds(Collection<Long> permissionIds) {
        List<Long> normalized = this.normalizePositiveIds(permissionIds);
        if (normalized.isEmpty()) {
            return new ArrayList<Long>();
        }
        List permissions = this.permissionMapper.selectBatchIds(normalized);
        if (CollectionUtils.isEmpty((Collection)permissions)) {
            return new ArrayList<Long>();
        }
        List<MenuPO> menus = this.listActiveMenus();
        if (menus.isEmpty()) {
            return new ArrayList<Long>();
        }
        Map menusByCode = menus.stream().filter(menu -> StringUtils.hasText((String)menu.getMenuCode())).collect(Collectors.toMap(MenuPO::getMenuCode, menu -> menu, (left, right) -> left, LinkedHashMap::new));
        Map menusByRequiredPermission = menus.stream().filter(menu -> StringUtils.hasText((String)menu.getRequiredPermissionCode())).collect(Collectors.toMap(MenuPO::getRequiredPermissionCode, menu -> menu, (left, right) -> left, LinkedHashMap::new));
        LinkedHashSet<Long> result = new LinkedHashSet<Long>();
        for (PermissionPO permission : permissions) {
            if (permission == null || !Boolean.TRUE.equals(permission.getActive())) continue;
            MenuPO menu2 = this.resolveMenu(permission, menusByCode, menusByRequiredPermission);
            this.addMenuAndParents(menu2, menusByCode, result);
        }
        return new ArrayList<Long>(result);
    }

    public PermissionTreeVO mergeCapabilityTree(PermissionTreeVO permissionRoot, PermissionTreeVO menuTree, Collection<Long> selectedPermissionIds) {
        HashSet<Long> selected = new HashSet<Long>(this.normalizePositiveIds(selectedPermissionIds));
        PermissionTreeVO root = permissionRoot == null ? PermissionTreeVO.builder().id(0L).has(Boolean.FALSE).leaf(Boolean.FALSE).childList(new ArrayList<PermissionTreeVO>()).build() : permissionRoot;
        root.setNodeType(NODE_TYPE_ROOT);
        root.setHas(Boolean.FALSE);
        PermissionTreeVO menus = menuTree == null ? PermissionTreeVO.builder().id(-1L).permissionCode("menu").permissionName("\u83dc\u5355\u4e0e\u64cd\u4f5c\u6743\u9650").has(Boolean.FALSE).leaf(Boolean.FALSE).active(Boolean.TRUE).childList(new ArrayList<PermissionTreeVO>()).build() : menuTree;
        menus.setPermissionName("\u83dc\u5355\u4e0e\u64cd\u4f5c\u6743\u9650");
        menus.setDescription("\u83dc\u5355\u63a7\u5236\u9875\u9762\u8bbf\u95ee\uff1b\u6309\u94ae\u6743\u9650\u4f1a\u81ea\u52a8\u5305\u542b\u6240\u5c5e\u83dc\u5355\u8bbf\u95ee\u80fd\u529b");
        menus.setNodeType(NODE_TYPE_MENU_GROUP);
        menus.setHas(Boolean.FALSE);
        LinkedHashMap<String, PermissionTreeVO> menuNodes = new LinkedHashMap<String, PermissionTreeVO>();
        this.markMenuNodes(menus, menuNodes);
        Map requiredPermissionByMenu = this.listActiveMenus().stream().filter(menu -> StringUtils.hasText((String)menu.getMenuCode())).filter(menu -> StringUtils.hasText((String)menu.getRequiredPermissionCode())).collect(Collectors.toMap(MenuPO::getMenuCode, MenuPO::getRequiredPermissionCode, (left, right) -> left, LinkedHashMap::new));
        ArrayList<PermissionTreeVO> mappedActions = new ArrayList<PermissionTreeVO>();
        this.detachMappedPermissions(root, mappedActions, true);
        for (PermissionTreeVO action : mappedActions) {
            PermissionTreeVO menu2;
            if (!StringUtils.hasText((String)action.getMenuCode()) || (menu2 = (PermissionTreeVO)menuNodes.get(action.getMenuCode())) == null || Objects.equals(action.getPermissionCode(), requiredPermissionByMenu.get(action.getMenuCode()))) continue;
            action.setNodeType(NODE_TYPE_ACTION);
            action.setParentId(menu2.getId());
            action.setLeaf(Boolean.TRUE);
            action.setChildList(null);
            action.setHas(action.getId() != null && selected.contains(action.getId()));
            if (menu2.getChildList() == null) {
                menu2.setChildList(new ArrayList<PermissionTreeVO>());
            }
            menu2.getChildList().add(action);
            menu2.setLeaf(Boolean.FALSE);
        }
        this.markRemainingPermissionNodes(root, true, selected);
        if (root.getChildList() == null) {
            root.setChildList(new ArrayList<PermissionTreeVO>());
        }
        root.getChildList().add(0, menus);
        root.setLeaf(Boolean.FALSE);
        return root;
    }

    private MenuPO resolveMenu(PermissionPO permission, Map<String, MenuPO> menusByCode, Map<String, MenuPO> menusByRequiredPermission) {
        MenuPO explicit;
        if (StringUtils.hasText((String)permission.getMenuCode()) && (explicit = menusByCode.get(permission.getMenuCode())) != null) {
            return explicit;
        }
        if (!StringUtils.hasText((String)permission.getPermissionCode())) {
            return null;
        }
        String permissionCode = permission.getPermissionCode();
        MenuPO exact = menusByRequiredPermission.get(permissionCode);
        if (exact != null) {
            return exact;
        }
        int separator = permissionCode.lastIndexOf(58);
        if (separator <= 0) {
            return null;
        }
        return menusByRequiredPermission.get(permissionCode.substring(0, separator) + ":read");
    }

    private void addMenuAndParents(MenuPO menu, Map<String, MenuPO> menusByCode, Set<Long> result) {
        HashSet<String> visited = new HashSet<String>();
        MenuPO current = menu;
        while (current != null && current.getId() != null && Boolean.TRUE.equals(current.getActive()) && StringUtils.hasText((String)current.getMenuCode()) && visited.add(current.getMenuCode())) {
            result.add(current.getId());
            current = StringUtils.hasText((String)current.getParentCode()) ? menusByCode.get(current.getParentCode()) : null;
        }
    }

    private void markMenuNodes(PermissionTreeVO node, Map<String, PermissionTreeVO> menuNodes) {
        if (node == null) {
            return;
        }
        if (StringUtils.hasText((String)node.getPermissionCode()) && node.getPermissionCode().startsWith(MENU_PERMISSION_PREFIX)) {
            String menuCode = node.getPermissionCode().substring(MENU_PERMISSION_PREFIX.length());
            node.setMenuCode(menuCode);
            node.setNodeType(NODE_TYPE_MENU);
            menuNodes.put(menuCode, node);
        }
        if (node.getChildList() != null) {
            for (PermissionTreeVO child : node.getChildList()) {
                this.markMenuNodes(child, menuNodes);
            }
        }
    }

    private boolean detachMappedPermissions(PermissionTreeVO node, List<PermissionTreeVO> mapped, boolean root) {
        if (node == null) {
            return false;
        }
        List<PermissionTreeVO> children = node.getChildList();
        if (children != null) {
            Iterator<PermissionTreeVO> iterator = children.iterator();
            while (iterator.hasNext()) {
                PermissionTreeVO child = iterator.next();
                if (StringUtils.hasText((String)child.getMenuCode())) {
                    mapped.add(child);
                    iterator.remove();
                    continue;
                }
                if (this.detachMappedPermissions(child, mapped, false)) continue;
                iterator.remove();
            }
        }
        return root || Boolean.TRUE.equals(node.getLeaf()) || node.getChildList() != null && !node.getChildList().isEmpty();
    }

    private void markRemainingPermissionNodes(PermissionTreeVO node, boolean root, Set<Long> selected) {
        if (node == null) {
            return;
        }
        if (!root) {
            boolean group = node.getChildList() != null && !node.getChildList().isEmpty();
            node.setNodeType(group ? NODE_TYPE_PERMISSION_GROUP : NODE_TYPE_ACTION);
            node.setHas(!group && node.getId() != null && selected.contains(node.getId()));
        }
        if (node.getChildList() != null) {
            for (PermissionTreeVO child : node.getChildList()) {
                this.markRemainingPermissionNodes(child, false, selected);
            }
        }
    }

    private List<MenuPO> listActiveMenus() {
        List menus = this.menuMapper.selectList((Wrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(MenuPO::getActive, (Object)true)).orderByAsc(MenuPO::getSortOrder)).orderByAsc(BasePO::getId));
        return menus == null ? Collections.emptyList() : menus;
    }

    private List<Long> normalizePositiveIds(Collection<Long> values) {
        if (CollectionUtils.isEmpty(values)) {
            return new ArrayList<Long>();
        }
        return values.stream().filter(Objects::nonNull).filter(value -> value > 0L).distinct().collect(Collectors.toList());
    }
}

