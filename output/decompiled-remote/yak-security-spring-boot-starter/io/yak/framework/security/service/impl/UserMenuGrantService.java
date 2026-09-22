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
import io.yak.framework.security.common.po.RoleMenuPO;
import io.yak.framework.security.dao.mapper.MenuMapper;
import io.yak.framework.security.dao.mapper.RoleMenuMapper;
import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.service.UserRoleService;
import io.yak.framework.security.service.impl.PermissionMenuRelationService;
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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityUserMenuGrantService")
public class UserMenuGrantService {
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleService userRoleService;
    private final RolePermissionService rolePermissionService;
    private final PermissionMenuRelationService permissionMenuRelationService;

    public UserMenuGrantService(MenuMapper menuMapper, RoleMenuMapper roleMenuMapper, UserRoleService userRoleService, RolePermissionService rolePermissionService, PermissionMenuRelationService permissionMenuRelationService) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleService = userRoleService;
        this.rolePermissionService = rolePermissionService;
        this.permissionMenuRelationService = permissionMenuRelationService;
    }

    public MenuGrant resolve(Long userId) {
        if (userId == null) {
            return MenuGrant.empty();
        }
        List<Long> roleIds = this.normalizeIds(this.userRoleService.getRoleIdListByUserId(userId));
        List<Long> permissionIds = this.rolePermissionService.getPermissionIdListByRoleIdList(roleIds);
        return this.resolve(roleIds, permissionIds);
    }

    MenuGrant resolve(Collection<Long> roleIds, Collection<Long> permissionIds) {
        List<Long> normalizedRoleIds = this.normalizeIds(roleIds);
        if (normalizedRoleIds.isEmpty()) {
            return MenuGrant.empty();
        }
        List menus = this.menuMapper.selectList((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().orderByAsc(MenuPO::getSortOrder)).orderByAsc(BasePO::getId));
        if (CollectionUtils.isEmpty((Collection)menus)) {
            return MenuGrant.empty();
        }
        LinkedHashMap<Long, MenuPO> byId = new LinkedHashMap<Long, MenuPO>();
        LinkedHashMap<String, MenuPO> byCode = new LinkedHashMap<String, MenuPO>();
        HashMap<String, List<MenuPO>> childrenByParentCode = new HashMap<String, List<MenuPO>>();
        for (MenuPO menu : menus) {
            if (menu == null || menu.getId() == null || !StringUtils.hasText((String)menu.getMenuCode())) continue;
            byId.put(menu.getId(), menu);
            byCode.put(menu.getMenuCode(), menu);
            if (!StringUtils.hasText((String)menu.getParentCode())) continue;
            childrenByParentCode.computeIfAbsent(menu.getParentCode(), ignored -> new ArrayList()).add(menu);
        }
        LinkedHashSet<Long> selectedMenuIds = new LinkedHashSet<Long>();
        List relations = this.roleMenuMapper.selectList((Wrapper)Wrappers.lambdaQuery().in(RoleMenuPO::getRoleId, normalizedRoleIds));
        if (!CollectionUtils.isEmpty((Collection)relations)) {
            relations.stream().map(RoleMenuPO::getMenuId).filter(Objects::nonNull).filter(id -> id > 0L).forEach(selectedMenuIds::add);
        }
        selectedMenuIds.addAll(this.permissionMenuRelationService.inferMenuIds(permissionIds));
        if (selectedMenuIds.isEmpty()) {
            return MenuGrant.empty();
        }
        LinkedHashSet<String> effectiveCodes = new LinkedHashSet<String>();
        for (Long l : selectedMenuIds) {
            MenuPO selected = (MenuPO)byId.get(l);
            if (!this.isActive(selected)) continue;
            this.addDescendants(selected, childrenByParentCode, effectiveCodes, new HashSet<String>());
        }
        LinkedHashSet snapshot = new LinkedHashSet(effectiveCodes);
        for (String code : snapshot) {
            this.addParents((MenuPO)byCode.get(code), byCode, effectiveCodes, new HashSet<String>());
        }
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
        for (String code : effectiveCodes) {
            MenuPO menu = (MenuPO)byCode.get(code);
            if (!this.isActive(menu) || !StringUtils.hasText((String)menu.getRequiredPermissionCode())) continue;
            linkedHashSet.add(menu.getRequiredPermissionCode());
        }
        return new MenuGrant(new ArrayList<String>(effectiveCodes), new ArrayList<String>(linkedHashSet));
    }

    public List<String> getMenuCodesByUserId(Long userId) {
        return this.resolve(userId).getMenuCodes();
    }

    public List<String> getPermissionCodesByUserId(Long userId) {
        return this.resolve(userId).getPermissionCodes();
    }

    private void addDescendants(MenuPO menu, Map<String, List<MenuPO>> childrenByParentCode, Set<String> result, Set<String> visited) {
        if (!this.isActive(menu) || !visited.add(menu.getMenuCode())) {
            return;
        }
        result.add(menu.getMenuCode());
        for (MenuPO child : childrenByParentCode.getOrDefault(menu.getMenuCode(), Collections.emptyList())) {
            this.addDescendants(child, childrenByParentCode, result, visited);
        }
    }

    private void addParents(MenuPO menu, Map<String, MenuPO> byCode, Set<String> result, Set<String> visited) {
        MenuPO current = menu;
        while (this.isActive(current) && visited.add(current.getMenuCode())) {
            result.add(current.getMenuCode());
            current = StringUtils.hasText((String)current.getParentCode()) ? byCode.get(current.getParentCode()) : null;
        }
    }

    private boolean isActive(MenuPO menu) {
        return menu != null && Boolean.TRUE.equals(menu.getActive()) && StringUtils.hasText((String)menu.getMenuCode());
    }

    private List<Long> normalizeIds(Collection<Long> values) {
        if (CollectionUtils.isEmpty(values)) {
            return new ArrayList<Long>();
        }
        return values.stream().filter(Objects::nonNull).filter(value -> value > 0L).distinct().collect(Collectors.toList());
    }

    public static final class MenuGrant {
        private final List<String> menuCodes;
        private final List<String> permissionCodes;

        private MenuGrant(List<String> menuCodes, List<String> permissionCodes) {
            this.menuCodes = Collections.unmodifiableList(new ArrayList<String>(menuCodes));
            this.permissionCodes = Collections.unmodifiableList(new ArrayList<String>(permissionCodes));
        }

        public static MenuGrant empty() {
            return new MenuGrant(Collections.emptyList(), Collections.emptyList());
        }

        public List<String> getMenuCodes() {
            return this.menuCodes;
        }

        public List<String> getPermissionCodes() {
            return this.permissionCodes;
        }
    }
}

