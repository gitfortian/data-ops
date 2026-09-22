/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.dao;

import io.yak.framework.security.common.entity.dept.Dept;
import io.yak.framework.security.common.entity.dept.DeptBrief;
import java.util.List;

public interface DeptDao {
    public List<Dept> selectAllAndAscOrderByLevel();

    public Dept selectByDeptId(Long var1);

    public List<Dept> selectListByParentId(Long var1);

    public int countByParentId(Long var1);

    public int countByNameAndParentId(String var1, Long var2, Long var3);

    public void insert(Dept var1);

    public void update(Dept var1);

    public void updateLeaf(Long var1, boolean var2);

    public void updateLevel(Long var1, int var2);

    public boolean deleteByDeptId(Long var1);

    public List<Long> selectIdListByLikeDeptName(String var1);

    public DeptBrief selectBriefByDeptId(Long var1);

    public List<Long> selectAllDeptIdList();

    public List<Long> selectIdListByParentId(Long var1);

    public void insertBatch(List<Dept> var1);

    public List<DeptBrief> selectAllDeptBriefList();
}

