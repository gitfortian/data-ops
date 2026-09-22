/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.dto.project.ProjectQueryDTO;
import io.yak.framework.security.common.entity.project.ProjectBrief;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.user.CurrentUserVO;
import io.yak.framework.security.dao.ProjectDao;
import io.yak.framework.security.service.UserProjectService;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserProjectResolver {
    private final ProjectDao projectDao;
    private final UserProjectService userProjectService;

    public CurrentUserProjectResolver(ProjectDao projectDao, UserProjectService userProjectService) {
        this.projectDao = projectDao;
        this.userProjectService = userProjectService;
    }

    public List<ProjectBriefVO> resolve(CurrentUserVO user) {
        if (user == null || user.getId() == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> projectIds = this.isRoot(user) ? this.allProjectIds() : new LinkedHashSet<Long>(this.userProjectService.getProjectIdListByUserIdList(Collections.singletonList(user.getId())));
        return this.resolveProjectIds((Set<Long>)projectIds);
    }

    public List<ProjectBriefVO> resolve(CurrentUserVO user, Collection<Long> authorizedProjectIds) {
        if (user == null || user.getId() == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> projectIds = this.isRoot(user) ? this.allProjectIds() : new LinkedHashSet<Long>(authorizedProjectIds == null ? Collections.emptySet() : authorizedProjectIds);
        return this.resolveProjectIds(projectIds);
    }

    private List<ProjectBriefVO> resolveProjectIds(Set<Long> projectIds) {
        projectIds.remove(null);
        if (projectIds.isEmpty()) {
            return Collections.emptyList();
        }
        ProjectQueryDTO query = new ProjectQueryDTO();
        query.setPage(1);
        query.setSize(projectIds.size());
        query.setRunning(true);
        List projects = this.projectDao.selectPageByDeptIdListAndProjectIdList(query, null, new ArrayList<Long>(projectIds)).getRecords();
        return CopyBeanUtil.copyList(projects, ProjectBriefVO.class);
    }

    private boolean isRoot(CurrentUserVO user) {
        return user.getPermissionCodes() != null && user.getPermissionCodes().contains("security:root");
    }

    private Set<Long> allProjectIds() {
        List<ProjectBrief> projects = this.projectDao.selectAllBriefList();
        if (projects == null || projects.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<Long>();
        projects.stream().filter(Objects::nonNull).map(ProjectBrief::getId).filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }
}

