/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.baomidou.mybatisplus.core.toolkit.support.SFunction
 *  lombok.Generated
 *  org.springframework.stereotype.Repository
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.yak.framework.security.common.entity.dept.Dept;
import io.yak.framework.security.common.entity.dept.DeptBrief;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.DeptPO;
import io.yak.framework.security.dao.DeptDao;
import io.yak.framework.security.dao.mapper.DeptMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.DatabaseNumberUtils;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Generated;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class DeptDaoImpl
implements DeptDao {
    private final DeptMapper deptMapper;

    @Override
    public List<Dept> selectAllAndAscOrderByLevel() {
        List departments = this.deptMapper.selectList((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().orderByAsc(DeptPO::getLevel)).orderByAsc(BasePO::getId));
        return CopyBeanUtil.copyList(departments, Dept.class);
    }

    @Override
    public Dept selectByDeptId(Long deptId) {
        if (deptId == null) {
            return null;
        }
        return CopyBeanUtil.copy(this.deptMapper.selectById(deptId), Dept.class);
    }

    @Override
    public List<Dept> selectListByParentId(Long parentId) {
        List departmentList = this.deptMapper.selectList((Wrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(DeptPO::getParentId, (Object)parentId)).orderByAsc(DeptPO::getLevel)).orderByAsc(BasePO::getId));
        return CopyBeanUtil.copyList(departmentList, Dept.class);
    }

    @Override
    public int countByParentId(Long parentId) {
        Long count = this.deptMapper.selectCount((Wrapper)Wrappers.lambdaQuery().eq(DeptPO::getParentId, (Object)parentId));
        return Math.toIntExact(count);
    }

    @Override
    public int countByNameAndParentId(String deptName, Long parentId, Long excludedDeptId) {
        Long count = this.deptMapper.selectCount((Wrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(DeptPO::getDeptName, (Object)deptName)).eq(DeptPO::getParentId, (Object)parentId)).ne(excludedDeptId != null, BasePO::getId, (Object)excludedDeptId));
        return Math.toIntExact(count);
    }

    @Override
    public void insert(Dept dept) {
        if (dept == null) {
            return;
        }
        DeptPO deptPO = CopyBeanUtil.copy(dept, DeptPO.class);
        this.deptMapper.insert(deptPO);
        dept.setId(deptPO.getId());
    }

    @Override
    public void update(Dept dept) {
        if (dept == null || dept.getId() == null) {
            return;
        }
        this.deptMapper.updateById(CopyBeanUtil.copy(dept, DeptPO.class));
    }

    @Override
    public void updateLeaf(Long deptId, boolean leaf) {
        if (deptId == null) {
            return;
        }
        this.deptMapper.update(null, (Wrapper)((LambdaUpdateWrapper)Wrappers.lambdaUpdate().eq(BasePO::getId, (Object)deptId)).set(DeptPO::getLeaf, (Object)leaf));
    }

    @Override
    public void updateLevel(Long deptId, int level) {
        if (deptId == null) {
            return;
        }
        this.deptMapper.update(null, (Wrapper)((LambdaUpdateWrapper)Wrappers.lambdaUpdate().eq(BasePO::getId, (Object)deptId)).set(DeptPO::getLevel, (Object)level));
    }

    @Override
    public boolean deleteByDeptId(Long deptId) {
        return deptId != null && this.deptMapper.deleteById(deptId) > 0;
    }

    @Override
    public List<Long> selectIdListByLikeDeptName(String deptName) {
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)Wrappers.lambdaQuery().select(new SFunction[]{BasePO::getId}).like(StringUtils.hasText((String)deptName), DeptPO::getDeptName, (Object)deptName);
        return this.selectIdList((LambdaQueryWrapper<DeptPO>)wrapper);
    }

    @Override
    public DeptBrief selectBriefByDeptId(Long deptId) {
        DeptPO department = (DeptPO)this.deptMapper.selectOne((Wrapper)this.briefQuery().eq(BasePO::getId, (Object)deptId));
        return CopyBeanUtil.copy(department, DeptBrief.class);
    }

    @Override
    public List<Long> selectAllDeptIdList() {
        return this.selectIdList((LambdaQueryWrapper<DeptPO>)Wrappers.lambdaQuery().select(new SFunction[]{BasePO::getId}));
    }

    @Override
    public List<Long> selectIdListByParentId(Long parentId) {
        return this.selectIdList((LambdaQueryWrapper<DeptPO>)((LambdaQueryWrapper)Wrappers.lambdaQuery().select(new SFunction[]{BasePO::getId}).eq(DeptPO::getParentId, (Object)parentId)));
    }

    @Override
    public void insertBatch(List<Dept> deptList) {
        if (deptList == null || deptList.isEmpty()) {
            return;
        }
        CopyBeanUtil.copyList(deptList, DeptPO.class).forEach(arg_0 -> ((DeptMapper)this.deptMapper).insert(arg_0));
    }

    @Override
    public List<DeptBrief> selectAllDeptBriefList() {
        List departments = this.deptMapper.selectList((Wrapper)((LambdaQueryWrapper)this.briefQuery().orderByAsc(DeptPO::getLevel)).orderByAsc(BasePO::getId));
        return CopyBeanUtil.copyList(departments, DeptBrief.class);
    }

    private LambdaQueryWrapper<DeptPO> briefQuery() {
        return Wrappers.lambdaQuery().select(new SFunction[]{BasePO::getId, DeptPO::getDeptName, DeptPO::getDescription, DeptPO::getParentId, DeptPO::getLeaf, DeptPO::getLevel});
    }

    private List<Long> selectIdList(LambdaQueryWrapper<DeptPO> wrapper) {
        return this.deptMapper.selectObjs((Wrapper)wrapper).stream().map(DatabaseNumberUtils::toLong).collect(Collectors.toList());
    }

    @Generated
    public DeptDaoImpl(DeptMapper deptMapper) {
        this.deptMapper = deptMapper;
    }
}

