/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.MenuPO;
import io.yak.framework.security.common.po.RoleMenuPO;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import io.yak.framework.security.dao.PermissionDao;
import io.yak.framework.security.dao.mapper.MenuMapper;
import io.yak.framework.security.dao.mapper.RoleMenuMapper;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.UserRoleService;
import io.yak.framework.security.service.impl.MenuSelectionCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityMenuAuthorizationService")
public class MenuAuthorizationService {
    private static final String MENU_PERMISSION_PREFIX = "menu:";
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final PermissionDao permissionDao;
    private final UserRoleService userRoleService;
    private final PermissionCache permissionCache;

    public MenuAuthorizationService(MenuMapper menuMapper, RoleMenuMapper roleMenuMapper, PermissionDao permissionDao, UserRoleService userRoleService, PermissionCache permissionCache) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.permissionDao = permissionDao;
        this.userRoleService = userRoleService;
        this.permissionCache = permissionCache;
    }

    public List<MenuPO> listMenus() {
        List menus = this.menuMapper.selectList((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().orderByAsc(MenuPO::getSortOrder)).orderByAsc(BasePO::getId));
        return menus == null ? new ArrayList() : menus;
    }

    public List<Long> getMenuIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return new ArrayList<Long>();
        }
        return this.getMenuIdsByRoleIds(Collections.singletonList(roleId));
    }

    public List<Long> getMenuIdsByRoleIds(Collection<Long> roleIds) {
        List<Long> normalizedRoleIds = this.normalizePositiveIds(roleIds);
        if (normalizedRoleIds.isEmpty()) {
            return new ArrayList<Long>();
        }
        List relations = this.roleMenuMapper.selectList((Wrapper)Wrappers.lambdaQuery().in(RoleMenuPO::getRoleId, normalizedRoleIds));
        if (CollectionUtils.isEmpty((Collection)relations)) {
            return new ArrayList<Long>();
        }
        return relations.stream().map(RoleMenuPO::getMenuId).filter(Objects::nonNull).filter(id -> id > 0L).distinct().collect(Collectors.toList());
    }

    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void saveRoleMenus(Long roleId, Collection<Long> menuIds) {
        if (roleId == null) {
            return;
        }
        this.insertRoleMenus(roleId, menuIds);
        this.permissionCache.invalidateRole(roleId);
    }

    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateRoleMenus(Long roleId, Collection<Long> menuIds) {
        if (roleId == null) {
            return;
        }
        this.roleMenuMapper.delete((Wrapper)Wrappers.lambdaQuery().eq(RoleMenuPO::getRoleId, (Object)roleId));
        this.insertRoleMenus(roleId, menuIds);
        this.permissionCache.invalidateRole(roleId);
    }

    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deleteRoleMenus(Long roleId) {
        if (roleId == null) {
            return;
        }
        this.roleMenuMapper.delete((Wrapper)Wrappers.lambdaQuery().eq(RoleMenuPO::getRoleId, (Object)roleId));
        this.permissionCache.invalidateRole(roleId);
    }

    public Set<Long> getMenuBoundPermissionIds() {
        Set<String> codes = this.getMenuBoundPermissionCodes();
        if (codes.isEmpty()) {
            return Collections.emptySet();
        }
        return this.activePermissionsByCode().entrySet().stream().filter(entry -> codes.contains(entry.getKey())).map(Map.Entry::getValue).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<Long> getRequiredPermissionIdsByRoleId(Long roleId) {
        return this.getRequiredPermissionIdsByRoleIds(roleId == null ? Collections.emptyList() : Collections.singletonList(roleId));
    }

    public List<Long> getRequiredPermissionIdsByRoleIds(Collection<Long> roleIds) {
        List<Long> menuIds = this.getMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return new ArrayList<Long>();
        }
        Map<Long, MenuPO> menusById = this.menusByIds(menuIds);
        Map<String, Long> permissionsByCode = this.activePermissionsByCode();
        LinkedHashSet<Long> result = new LinkedHashSet<Long>();
        for (Long menuId : menuIds) {
            Long permissionId;
            MenuPO menu = menusById.get(menuId);
            if (!this.isAvailable(menu) || !StringUtils.hasText((String)menu.getRequiredPermissionCode()) || (permissionId = permissionsByCode.get(menu.getRequiredPermissionCode())) == null) continue;
            result.add(permissionId);
        }
        return new ArrayList<Long>(result);
    }

    public Set<String> getMenuBoundPermissionCodes() {
        return this.listMenus().stream().map(MenuPO::getRequiredPermissionCode).filter(StringUtils::hasText).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<String> getMenuCodesByUserId(Long userId) {
        if (userId == null) {
            return new ArrayList<String>();
        }
        List<Long> roleIds = this.userRoleService.getRoleIdListByUserId(userId);
        List<Long> menuIds = this.getMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return new ArrayList<String>();
        }
        List<MenuPO> menus = this.listMenus();
        Map byId = menus.stream().filter(menu -> menu.getId() != null).collect(Collectors.toMap(BasePO::getId, menu -> menu, (left, right) -> left, LinkedHashMap::new));
        Map byCode = menus.stream().filter(menu -> StringUtils.hasText((String)menu.getMenuCode())).collect(Collectors.toMap(MenuPO::getMenuCode, menu -> menu, (left, right) -> left, LinkedHashMap::new));
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        for (Long menuId : menuIds) {
            MenuPO menu2 = (MenuPO)byId.get(menuId);
            if (!this.isAvailable(menu2)) continue;
            this.addMenuAndParents(menu2, byCode, result);
        }
        return new ArrayList<String>(result);
    }

    public PermissionTreeVO buildMenuTree(Collection<Long> selectedMenuIds) {
        HashSet<Long> selected = new HashSet<Long>(this.normalizePositiveIds(selectedMenuIds));
        PermissionTreeVO group = PermissionTreeVO.builder().id(-1L).has(Boolean.TRUE).permissionCode("menu").permissionName("\u83dc\u5355\u6743\u9650").parentId(0L).leaf(Boolean.FALSE).description("\u63a7\u5236\u89d2\u8272\u767b\u5f55\u540e\u53ef\u89c1\u548c\u53ef\u8bbf\u95ee\u7684\u9875\u9762\u5165\u53e3").active(Boolean.TRUE).declared(Boolean.FALSE).childList(new ArrayList<PermissionTreeVO>()).build();
        List<MenuPO> menus = this.listMenus();
        if (menus.isEmpty()) {
            return group;
        }
        LinkedHashMap<String, PermissionTreeVO> nodesByCode = new LinkedHashMap<String, PermissionTreeVO>();
        LinkedHashMap<String, MenuPO> menusByCode = new LinkedHashMap<String, MenuPO>();
        for (MenuPO menuPO : menus) {
            if (menuPO == null || menuPO.getId() == null || !StringUtils.hasText((String)menuPO.getMenuCode())) continue;
            PermissionTreeVO node = PermissionTreeVO.builder().id(MenuSelectionCodec.encodeMenuId(menuPO.getId())).has(selected.contains(menuPO.getId())).permissionCode(MENU_PERMISSION_PREFIX + menuPO.getMenuCode()).permissionName(menuPO.getMenuName()).leaf(Boolean.TRUE).description(this.buildDescription(menuPO)).active(this.isAvailable(menuPO)).declared(Boolean.FALSE).childList(new ArrayList<PermissionTreeVO>()).build();
            nodesByCode.put(menuPO.getMenuCode(), node);
            menusByCode.put(menuPO.getMenuCode(), menuPO);
        }
        for (Map.Entry entry : nodesByCode.entrySet()) {
            PermissionTreeVO parent;
            MenuPO menu = (MenuPO)menusByCode.get(entry.getKey());
            PermissionTreeVO node = (PermissionTreeVO)entry.getValue();
            PermissionTreeVO permissionTreeVO = parent = StringUtils.hasText((String)menu.getParentCode()) ? (PermissionTreeVO)nodesByCode.get(menu.getParentCode()) : group;
            if (parent == null) {
                parent = group;
            }
            node.setParentId(parent.getId());
            parent.setLeaf(Boolean.FALSE);
            parent.getChildList().add(node);
        }
        return group;
    }

    private void insertRoleMenus(Long roleId, Collection<Long> menuIds) {
        List<Long> normalizedMenuIds = this.normalizePositiveIds(menuIds);
        if (normalizedMenuIds.isEmpty()) {
            return;
        }
        Map<Long, MenuPO> existingMenus = this.menusByIds(normalizedMenuIds);
        for (Long menuId : normalizedMenuIds) {
            MenuPO menu = existingMenus.get(menuId);
            if (menu == null || !Boolean.TRUE.equals(menu.getActive())) continue;
            RoleMenuPO relation = new RoleMenuPO();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            this.roleMenuMapper.insert(relation);
        }
    }

    private Map<Long, MenuPO> menusByIds(Collection<Long> menuIds) {
        List<Long> normalized = this.normalizePositiveIds(menuIds);
        if (normalized.isEmpty()) {
            return Collections.emptyMap();
        }
        List menus = this.menuMapper.selectBatchIds(normalized);
        if (CollectionUtils.isEmpty((Collection)menus)) {
            return Collections.emptyMap();
        }
        return menus.stream().filter(menu -> menu.getId() != null).collect(Collectors.toMap(BasePO::getId, menu -> menu, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<String, Long> activePermissionsByCode() {
        HashMap<String, Long> result = new HashMap<String, Long>();
        for (Permission permission : this.permissionDao.selectAllAndAscOrderByLevel()) {
            if (permission == null || permission.getId() == null || !Boolean.TRUE.equals(permission.getActive()) || !StringUtils.hasText((String)permission.getPermissionCode())) continue;
            result.putIfAbsent(permission.getPermissionCode(), permission.getId());
        }
        return result;
    }

    private void addMenuAndParents(MenuPO menu, Map<String, MenuPO> menusByCode, Set<String> result) {
        HashSet<String> visited = new HashSet<String>();
        MenuPO current = menu;
        while (current != null && StringUtils.hasText((String)current.getMenuCode()) && visited.add(current.getMenuCode())) {
            result.add(current.getMenuCode());
            current = StringUtils.hasText((String)current.getParentCode()) ? menusByCode.get(current.getParentCode()) : null;
        }
    }

    private boolean isAvailable(MenuPO menu) {
        return menu != null && Boolean.TRUE.equals(menu.getActive()) && Boolean.TRUE.equals(menu.getVisible());
    }

    private String buildDescription(MenuPO menu) {
        String description = menu.getDescription();
        if (!StringUtils.hasText((String)menu.getRoutePath())) {
            return description;
        }
        return StringUtils.hasText((String)description) ? description + "\uff08" + menu.getRoutePath() + "\uff09" : menu.getRoutePath();
    }

    private List<Long> normalizePositiveIds(Collection<Long> values) {
        if (CollectionUtils.isEmpty(values)) {
            return new ArrayList<Long>();
        }
        return values.stream().filter(Objects::nonNull).filter(value -> value > 0L).distinct().collect(Collectors.toList());
    }
}

