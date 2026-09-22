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
import io.yak.framework.security.common.dto.project.ProjectBriefQueryDTO;
import io.yak.framework.security.common.dto.project.ProjectQueryDTO;
import io.yak.framework.security.common.entity.project.Project;
import io.yak.framework.security.common.entity.project.ProjectBrief;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.ProjectPO;
import io.yak.framework.security.dao.ProjectDao;
import io.yak.framework.security.dao.mapper.ProjectMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.Collections;
import java.util.List;
import lombok.Generated;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ProjectDaoImpl
implements ProjectDao {
    private final ProjectMapper projectMapper;

    private static boolean hasItems(List<?> values) {
        return values != null && !values.isEmpty();
    }

    @Override
    public Project selectByProjectId(Long projectId) {
        if (projectId == null) {
            return null;
        }
        return CopyBeanUtil.copy(this.projectMapper.selectById(projectId), Project.class);
    }

    @Override
    public void insert(Project project) {
        ProjectPO projectPO = CopyBeanUtil.copy(project, ProjectPO.class);
        this.projectMapper.insert(projectPO);
        project.setId(projectPO.getId());
    }

    @Override
    public void deleteByProjectId(Long projectId) {
        if (projectId != null) {
            this.projectMapper.deleteById(projectId);
        }
    }

    @Override
    public void update(Project project) {
        this.projectMapper.updateById(CopyBeanUtil.copy(project, ProjectPO.class));
    }

    @Override
    public int selectCountByProjectNameAndNotProjectId(String projectName, Long projectId) {
        Long count = this.projectMapper.selectCount((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(ProjectPO::getProjectName, (Object)projectName)).ne(projectId != null, BasePO::getId, (Object)projectId));
        return Math.toIntExact(count);
    }

    @Override
    public IPage<ProjectBrief> selectBriefPage(ProjectBriefQueryDTO queryDTO) {
        Page page = Page.of((long)queryDTO.getPage(), (long)queryDTO.getSize());
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)this.briefQuery().like(StringUtils.hasText((String)queryDTO.getProjectName()), ProjectPO::getProjectName, (Object)queryDTO.getProjectName())).orderByDesc(BasePO::getId);
        IPage result = this.projectMapper.selectPage((IPage)page, (Wrapper)wrapper);
        return CopyBeanUtil.copyPage(result, ProjectBrief.class);
    }

    @Override
    public List<ProjectBrief> selectAllBriefList() {
        List projectPOList = this.projectMapper.selectList((Wrapper)this.briefQuery().orderByAsc(ProjectPO::getProjectName));
        return CopyBeanUtil.copyList(projectPOList, ProjectBrief.class);
    }

    @Override
    public IPage<Project> selectPageByDeptIdListAndProjectIdList(ProjectQueryDTO queryDTO, List<Long> deptIdList, List<Long> projectIdList) {
        Page page = Page.of((long)queryDTO.getPage(), (long)queryDTO.getSize());
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(queryDTO.getRunning() != null, ProjectPO::getRunning, (Object)queryDTO.getRunning())).eq(StringUtils.hasText((String)queryDTO.getProjectCode()), ProjectPO::getProjectCode, (Object)queryDTO.getProjectCode())).like(StringUtils.hasText((String)queryDTO.getProjectName()), ProjectPO::getProjectName, (Object)queryDTO.getProjectName())).in(ProjectDaoImpl.hasItems(deptIdList), ProjectPO::getDeptId, deptIdList)).in(ProjectDaoImpl.hasItems(projectIdList), BasePO::getId, projectIdList)).orderByDesc(BasePO::getId);
        IPage result = this.projectMapper.selectPage((IPage)page, (Wrapper)wrapper);
        return CopyBeanUtil.copyPage(result, Project.class);
    }

    @Override
    public List<Project> selectProjectBriefByProjectIds(List<Long> projectIds) {
        if (!ProjectDaoImpl.hasItems(projectIds)) {
            return Collections.emptyList();
        }
        List projectPOList = this.projectMapper.selectList((Wrapper)this.briefQuery().in(BasePO::getId, projectIds));
        return CopyBeanUtil.copyList(projectPOList, Project.class);
    }

    private LambdaQueryWrapper<ProjectPO> briefQuery() {
        return Wrappers.lambdaQuery().select(new SFunction[]{BasePO::getId, ProjectPO::getProjectCode, ProjectPO::getProjectName});
    }

    @Generated
    public ProjectDaoImpl(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }
}

