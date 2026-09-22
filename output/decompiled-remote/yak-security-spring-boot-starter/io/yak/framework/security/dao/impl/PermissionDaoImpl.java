/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  lombok.Generated
 *  org.springframework.stereotype.Repository
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.PermissionPO;
import io.yak.framework.security.dao.PermissionDao;
import io.yak.framework.security.dao.mapper.PermissionMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import lombok.Generated;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class PermissionDaoImpl
implements PermissionDao {
    private final PermissionMapper permissionMapper;

    @Override
    public List<Permission> selectAllAndAscOrderByLevel() {
        List permissionPOList = this.permissionMapper.selectList((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().orderByAsc(PermissionPO::getLevel)).orderByAsc(BasePO::getId));
        return CopyBeanUtil.copyList(permissionPOList, Permission.class);
    }

    @Override
    public void insertBatch(List<Permission> permissionList) {
        if (permissionList == null || permissionList.isEmpty()) {
            return;
        }
        CopyBeanUtil.copyList(permissionList, PermissionPO.class).forEach(arg_0 -> ((PermissionMapper)this.permissionMapper).insert(arg_0));
    }

    @Override
    public int deleteById(Long permissionId) {
        if (permissionId == null) {
            return 0;
        }
        return this.permissionMapper.deleteById(permissionId);
    }

    @Override
    public void synchronizeDeclared(List<Permission> permissions) {
        List existing = this.permissionMapper.selectList((Wrapper)Wrappers.lambdaQuery());
        HashMap byCode = new HashMap();
        existing.forEach(item -> byCode.put(item.getPermissionCode(), item));
        HashSet desiredCodes = new HashSet();
        permissions.stream().sorted(Comparator.comparing(Permission::getLevel)).forEach(item -> {
            desiredCodes.add(item.getPermissionCode());
            PermissionPO row = (PermissionPO)byCode.get(item.getPermissionCode());
            if (row == null) {
                row = CopyBeanUtil.copy(item, PermissionPO.class);
                this.permissionMapper.insert(row);
                byCode.put(row.getPermissionCode(), row);
            } else {
                row.setPermissionName(item.getPermissionName());
                row.setDescription(item.getDescription());
                row.setLeaf(item.getLeaf());
                row.setLevel(item.getLevel());
                if (StringUtils.hasText((String)item.getMenuCode())) {
                    row.setMenuCode(item.getMenuCode());
                }
                row.setActive(true);
            }
            if (item.getParentCode() != null) {
                PermissionPO parent = (PermissionPO)byCode.get(item.getParentCode());
                if (parent == null) {
                    throw new IllegalStateException("Missing permission group: " + item.getParentCode());
                }
                row.setParentId(parent.getId());
            } else {
                row.setParentId(0L);
            }
            row.setDeclared(true);
            this.permissionMapper.updateById(row);
        });
        existing.stream().filter(item -> Boolean.TRUE.equals(item.getDeclared())).filter(item -> !desiredCodes.contains(item.getPermissionCode())).filter(item -> Boolean.TRUE.equals(item.getActive())).forEach(item -> {
            item.setActive(false);
            this.permissionMapper.updateById(item);
        });
    }

    @Generated
    public PermissionDaoImpl(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }
}

