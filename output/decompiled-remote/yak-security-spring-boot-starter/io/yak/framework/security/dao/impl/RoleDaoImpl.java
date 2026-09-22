/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.baomidou.mybatisplus.core.toolkit.support.SFunction
 *  com.baomidou.mybatisplus.extension.plugins.pagination.Page
 *  lombok.Generated
 *  org.springframework.stereotype.Repository
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.security.common.dto.role.RoleQueryDTO;
import io.yak.framework.security.common.entity.role.Role;
import io.yak.framework.security.common.entity.role.RoleBrief;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.RolePO;
import io.yak.framework.security.dao.RoleDao;
import io.yak.framework.security.dao.mapper.RoleMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.Collections;
import java.util.List;
import lombok.Generated;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class RoleDaoImpl
implements RoleDao {
    private final RoleMapper roleMapper;

    @Override
    public Role selectByRoleName(String roleName) {
        if (!StringUtils.hasText((String)roleName)) {
            return null;
        }
        RolePO rolePO = (RolePO)this.roleMapper.selectOne((Wrapper)Wrappers.lambdaQuery().eq(RolePO::getRoleName, (Object)roleName));
        return CopyBeanUtil.copy(rolePO, Role.class);
    }

    @Override
    public Role selectByRoleId(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return CopyBeanUtil.copy(this.roleMapper.selectById(roleId), Role.class);
    }

    @Override
    public IPage<Role> selectPage(RoleQueryDTO queryDTO) {
        Page page = Page.of((long)queryDTO.getPage(), (long)queryDTO.getSize());
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)Wrappers.lambdaQuery().eq(queryDTO.getId() != null, BasePO::getId, (Object)queryDTO.getId());
        if (StringUtils.hasText((String)queryDTO.getRoleCode())) {
            wrapper.eq(RolePO::getRoleCode, (Object)queryDTO.getRoleCode());
        } else {
            ((LambdaQueryWrapper)wrapper.like(StringUtils.hasText((String)queryDTO.getRoleName()), RolePO::getRoleName, (Object)queryDTO.getRoleName())).like(StringUtils.hasText((String)queryDTO.getDescription()), RolePO::getDescription, (Object)queryDTO.getDescription());
        }
        wrapper.orderByDesc(BasePO::getCreateTime);
        IPage result = this.roleMapper.selectPage((IPage)page, (Wrapper)wrapper);
        return CopyBeanUtil.copyPage(result, Role.class);
    }

    @Override
    public void insert(Role role) {
        RolePO rolePO = CopyBeanUtil.copy(role, RolePO.class);
        this.roleMapper.insert(rolePO);
        role.setId(rolePO.getId());
    }

    @Override
    public void deleteByRoleId(Long roleId) {
        if (roleId != null) {
            this.roleMapper.deleteById(roleId);
        }
    }

    @Override
    public void update(Role role) {
        this.roleMapper.updateById(CopyBeanUtil.copy(role, RolePO.class));
    }

    @Override
    public List<RoleBrief> selectBriefListByRoleNameAndDescOrderByCreateTime(String roleName) {
        List rolePOList = this.roleMapper.selectList((Wrapper)((LambdaQueryWrapper)this.briefQuery().like(StringUtils.hasText((String)roleName), RolePO::getRoleName, (Object)roleName)).orderByDesc(BasePO::getCreateTime));
        return CopyBeanUtil.copyList(rolePOList, RoleBrief.class);
    }

    @Override
    public List<RoleBrief> selectAllBrief() {
        List rolePOList = this.roleMapper.selectList((Wrapper)((LambdaQueryWrapper)this.briefQuery().orderByAsc(RolePO::getRoleName)).orderByAsc(BasePO::getId));
        return CopyBeanUtil.copyList(rolePOList, RoleBrief.class);
    }

    @Override
    public List<RoleBrief> selectBriefListByRoleIdList(List<Long> roleIdList) {
        if (roleIdList == null || roleIdList.isEmpty()) {
            return Collections.emptyList();
        }
        List rolePOList = this.roleMapper.selectList((Wrapper)this.briefQuery().in(BasePO::getId, roleIdList));
        return CopyBeanUtil.copyList(rolePOList, RoleBrief.class);
    }

    @Override
    public int selectCountByRoleNameAndNotRoleId(String roleName, Long roleId) {
        Long count = this.roleMapper.selectCount((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(RolePO::getRoleName, (Object)roleName)).ne(roleId != null, BasePO::getId, (Object)roleId));
        return Math.toIntExact(count);
    }

    private LambdaQueryWrapper<RolePO> briefQuery() {
        return Wrappers.lambdaQuery().select(new SFunction[]{BasePO::getId, RolePO::getRoleName});
    }

    @Generated
    public RoleDaoImpl(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }
}

