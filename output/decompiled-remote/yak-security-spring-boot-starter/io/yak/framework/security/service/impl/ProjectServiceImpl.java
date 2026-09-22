/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  io.yak.framework.common.PageData
 *  io.yak.framework.common.PagingData
 *  io.yak.framework.common.Result
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.oplog.OplogDTO;
import io.yak.framework.security.common.dto.project.ProjectBriefQueryDTO;
import io.yak.framework.security.common.dto.project.ProjectQueryDTO;
import io.yak.framework.security.common.dto.project.ProjectSaveDTO;
import io.yak.framework.security.common.dto.resource.ResourceDTO;
import io.yak.framework.security.common.entity.BaseEntity;
import io.yak.framework.security.common.entity.UserProject;
import io.yak.framework.security.common.entity.dept.Dept;
import io.yak.framework.security.common.entity.project.Project;
import io.yak.framework.security.common.entity.project.ProjectBrief;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.enums.project.ProjectUserCode;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.project.ProjectBriefVOWithUser;
import io.yak.framework.security.common.vo.project.ProjectDeleteCheckVO;
import io.yak.framework.security.common.vo.project.ProjectVO;
import io.yak.framework.security.common.vo.user.UserBasicVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.dao.ProjectDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.extend.ResourceExtend;
import io.yak.framework.security.service.DeptService;
import io.yak.framework.security.service.OplogService;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.service.UserProjectService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.JsonUtils;
import io.yak.framework.security.util.MathUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

@Service(value="yakSecurityProjectServiceImpl")
public class ProjectServiceImpl
implements ProjectService {
    private static final String PROJECT_CODE_PREFIX = "p";
    private static final int PROJECT_CODE_RANDOM_LENGTH = 7;
    private static final String OPERATION_CREATE = "\u65b0\u589e";
    private static final String OPERATION_EDIT = "\u7f16\u8f91";
    private static final String OPERATION_DELETE = "\u5220\u9664";
    private static final String OPERATION_ENABLE = "\u542f\u7528";
    private static final String OPERATION_DISABLE = "\u505c\u7528";
    private static final String OPERATION_OBJECT_PROJECT = "Project";
    private final ProjectDao projectDao;
    private final DeptService deptService;
    private final OplogService oplogService;
    private final UserService userService;
    private final UserProjectService userProjectService;
    private final ResourceExtend resourceExtend;

    public ProjectServiceImpl(ProjectDao projectDao, DeptService deptService, OplogService oplogService, UserService userService, UserProjectService userProjectService, ResourceExtend resourceExtend) {
        this.projectDao = projectDao;
        this.deptService = deptService;
        this.oplogService = oplogService;
        this.userService = userService;
        this.userProjectService = userProjectService;
        this.resourceExtend = resourceExtend;
    }

    @Override
    public ProjectVO getProjectDetailByProjectId(Long projectId) {
        Project project = this.getRequiredProject(projectId);
        ProjectVO projectVO = CopyBeanUtil.copy(project, ProjectVO.class);
        if (projectVO == null) {
            throw new IllegalStateException("\u9879\u76ee\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        List<Long> userIdList = this.userProjectService.getUserIdListByProjectId(projectId, ProjectUserCode.NORMAL);
        projectVO.setUserList(this.userService.getUserBriefListByUserIds(userIdList));
        List<Long> ownerIdList = this.userProjectService.getUserIdListByProjectId(projectId, ProjectUserCode.OWNER);
        projectVO.setOwnerList(this.userService.getUserBriefListByUserIds(ownerIdList));
        projectVO.setDeptList(this.deptService.getDeptBriefListByChildId(project.getDeptId()));
        projectVO.setCreateTime(project.getCreateTime());
        return projectVO;
    }

    @Override
    public ProjectBriefVO getProjectBriefByProjectId(Long projectId) {
        if (projectId == null) {
            return null;
        }
        Project project = this.projectDao.selectByProjectId(projectId);
        return CopyBeanUtil.copy(project, ProjectBriefVO.class);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public ProjectVO createProject(ProjectSaveDTO projectSaveDTO, String operator) {
        this.checkParam(projectSaveDTO, false);
        Project project = CopyBeanUtil.copy(projectSaveDTO, Project.class);
        if (project == null) {
            throw new IllegalStateException("\u9879\u76ee\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        project.setProjectCode(this.generateProjectCode());
        this.projectDao.insert(project);
        this.userProjectService.saveOwnerProject(project.getId(), projectSaveDTO.getOwnerIdList());
        this.userProjectService.saveUserProject(project.getId(), projectSaveDTO.getUserIdList());
        this.oplogService.saveOplog(new OplogDTO(operator, OPERATION_CREATE, OPERATION_OBJECT_PROJECT, project.getProjectName(), "'' -> " + project.getProjectName()));
        return this.getProjectDetailByProjectId(project.getId());
    }

    @Override
    public PagingData<ProjectVO> getProjectPage(ProjectQueryDTO queryDTO) {
        return this.getProjectPageInternal(queryDTO, null);
    }

    @Override
    public PagingData<ProjectVO> getProjectPage(ProjectQueryDTO queryDTO, List<Long> projectIdList) {
        return this.getProjectPageInternal(queryDTO, projectIdList);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deleteProjectByProjectId(Long projectId, String operator) {
        Project project = this.getRequiredProject(projectId);
        List<String> resourceList = this.listResourceOfProject(projectId);
        if (!CollectionUtils.isEmpty(resourceList)) {
            throw new YakSecurityException(ResultCode.PROJECT_DEL_RESOURCE_NOT_NULL);
        }
        this.userProjectService.deleteUserProjectByProjectId(projectId);
        this.userProjectService.deleteOwnerProjectByProjectId(projectId);
        this.projectDao.deleteByProjectId(projectId);
        this.oplogService.saveOplog(new OplogDTO(operator, OPERATION_DELETE, OPERATION_OBJECT_PROJECT, project.getProjectName(), project.getProjectName() + " -> ''"));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateProject(ProjectSaveDTO projectSaveDTO, String operator) {
        if (projectSaveDTO == null || projectSaveDTO.getId() == null) {
            throw new IllegalArgumentException("\u9879\u76ee\u4fe1\u606f\u548c\u9879\u76ee ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        this.getRequiredProject(projectSaveDTO.getId());
        this.checkParam(projectSaveDTO, true);
        Project project = CopyBeanUtil.copy(projectSaveDTO, Project.class);
        if (project == null) {
            throw new IllegalStateException("\u9879\u76ee\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        this.projectDao.update(project);
        if (projectSaveDTO.getUserIdList() != null) {
            this.userProjectService.updateUserProject(projectSaveDTO.getId(), projectSaveDTO.getUserIdList());
        }
        if (projectSaveDTO.getOwnerIdList() != null) {
            this.userProjectService.updateOwnerProject(projectSaveDTO.getId(), projectSaveDTO.getOwnerIdList());
        }
        this.oplogService.saveOplog(new OplogDTO(operator, OPERATION_EDIT, OPERATION_OBJECT_PROJECT, projectSaveDTO.getProjectName(), JsonUtils.toJson(projectSaveDTO)));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void changeProjectStatus(Long projectId, String operator) {
        Project project = this.getRequiredProject(projectId);
        boolean newRunningStatus = !Boolean.TRUE.equals(project.getRunning());
        project.setRunning(newRunningStatus);
        this.projectDao.update(project);
        this.oplogService.saveOplog(new OplogDTO(operator, newRunningStatus ? OPERATION_ENABLE : OPERATION_DISABLE, OPERATION_OBJECT_PROJECT, project.getProjectName(), "status:" + newRunningStatus));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void addProjectUser(Long projectId, Long userId, String operator) {
        Project project = this.getRequiredProject(projectId);
        this.checkUserId(userId);
        this.userProjectService.updateUserInformationAssociatedWithProject(projectId, Collections.singletonList(userId));
        this.saveRelationOplog(operator, OPERATION_CREATE, project, "\u589e\u52a0\u9879\u76ee\u7528\u6237\uff1a" + userId);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void delProjectUser(Long projectId, Long userId, String operator) {
        Project project = this.getRequiredProject(projectId);
        this.checkUserId(userId);
        this.userProjectService.delUserProject(projectId, Collections.singletonList(userId));
        this.saveRelationOplog(operator, OPERATION_DELETE, project, "\u5220\u9664\u9879\u76ee\u7528\u6237\uff1a" + userId);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void addProjectOwner(Long projectId, Long ownerId, String operator) {
        Project project = this.getRequiredProject(projectId);
        this.checkUserId(ownerId);
        this.userProjectService.updateOwnerInformationAssociatedWithProject(projectId, Collections.singletonList(ownerId));
        this.saveRelationOplog(operator, OPERATION_CREATE, project, "\u589e\u52a0\u9879\u76ee\u8d1f\u8d23\u4eba\uff1a" + ownerId);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void delProjectOwner(Long projectId, Long ownerId, String operator) {
        Project project = this.getRequiredProject(projectId);
        this.checkUserId(ownerId);
        this.userProjectService.delOwnerProject(projectId, Collections.singletonList(ownerId));
        this.saveRelationOplog(operator, OPERATION_DELETE, project, "\u5220\u9664\u9879\u76ee\u8d1f\u8d23\u4eba\uff1a" + ownerId);
    }

    @Override
    public List<ProjectBriefVO> getProjectBriefList() {
        ArrayList projectList = CopyBeanUtil.copyList(this.projectDao.selectAllBriefList(), ProjectBriefVO.class);
        return projectList == null ? new ArrayList() : projectList;
    }

    @Override
    public ProjectDeleteCheckVO checkBeforeDelete(Long projectId) {
        if (projectId == null) {
            return null;
        }
        return new ProjectDeleteCheckVO(projectId, this.listResourceOfProject(projectId));
    }

    @Override
    public PagingData<ProjectBriefVO> getProjectBriefPage(ProjectBriefQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u9879\u76ee\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        IPage<ProjectBrief> projectPage = this.projectDao.selectBriefPage(queryDTO);
        List<ProjectBriefVO> projectList = CopyBeanUtil.copyList(projectPage.getRecords(), ProjectBriefVO.class);
        if (projectList == null) {
            projectList = new ArrayList<ProjectBriefVO>();
        }
        return ProjectServiceImpl.toPagingData(projectList, projectPage);
    }

    @Override
    public boolean checkProjectExist(Long projectId) {
        return projectId != null && this.projectDao.selectByProjectId(projectId) != null;
    }

    @Override
    public Result<List<UserBriefVO>> unassignedByProjectId(Long projectId) {
        this.getRequiredProject(projectId);
        HashSet<Long> assignedUserIds = new HashSet<Long>();
        assignedUserIds.addAll(this.userProjectService.getUserIdListByProjectId(projectId, ProjectUserCode.NORMAL));
        assignedUserIds.addAll(this.userProjectService.getUserIdListByProjectId(projectId, ProjectUserCode.OWNER));
        List<UserBriefVO> allUserList = this.userService.getAllUserBriefList();
        if (CollectionUtils.isEmpty(allUserList)) {
            return Result.success(new ArrayList());
        }
        List unassignedUserList = allUserList.stream().filter(Objects::nonNull).filter(user -> user.getId() != null).filter(user -> !assignedUserIds.contains(user.getId())).collect(Collectors.toList());
        return Result.success(unassignedUserList);
    }

    @Override
    public Result<List<ProjectBriefVO>> getProjectBriefByUserId(Long userId) {
        if (userId == null) {
            return Result.buildParamIllegal((String)"\u7528\u6237 ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        List<Long> projectIdList = this.userProjectService.getProjectIdListByUserIdList(Collections.singletonList(userId));
        if (CollectionUtils.isEmpty(projectIdList)) {
            return Result.success(new ArrayList());
        }
        List projectList = projectIdList.stream().map(this::getProjectBriefByProjectId).filter(Objects::nonNull).collect(Collectors.toList());
        return Result.success(projectList);
    }

    @Override
    public List<ProjectBriefVOWithUser> listProjectBriefVOWithUserByProjectIds(List<Long> projectIdList) {
        List<Long> validProjectIds = this.normalizeIds(projectIdList);
        if (validProjectIds.isEmpty()) {
            return new ArrayList<ProjectBriefVOWithUser>();
        }
        List<Project> projectList = this.projectDao.selectProjectBriefByProjectIds(validProjectIds);
        if (CollectionUtils.isEmpty(projectList)) {
            return new ArrayList<ProjectBriefVOWithUser>();
        }
        List<UserProject> userProjectList = this.userProjectService.lisUserProjectByProjectIds(validProjectIds);
        Map<Long, Map<Integer, Set<Long>>> relationMap = this.buildProjectUserRelationMap(userProjectList);
        List<Long> userIdList = this.getAllRelationUserIds(userProjectList);
        List<UserBasicVO> userList = this.userService.getUserBasicListByUserIds(userIdList);
        Map<Long, UserBasicVO> userMap = this.buildUserBasicMap(userList);
        ArrayList<ProjectBriefVOWithUser> resultList = new ArrayList<ProjectBriefVOWithUser>(projectList.size());
        for (Project project : projectList) {
            ProjectBriefVOWithUser projectVO = CopyBeanUtil.copy(project, ProjectBriefVOWithUser.class);
            if (projectVO == null) continue;
            projectVO.setUserList(this.resolveUserBasicList(project.getId(), ProjectUserCode.NORMAL.getType(), relationMap, userMap));
            projectVO.setOwnerList(this.resolveUserBasicList(project.getId(), ProjectUserCode.OWNER.getType(), relationMap, userMap));
            resultList.add(projectVO);
        }
        return resultList;
    }

    private PagingData<ProjectVO> getProjectPageInternal(ProjectQueryDTO queryDTO, List<Long> additionalProjectIds) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u9879\u76ee\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        List<Long> projectIdFilter = this.resolveProjectIdFilter(queryDTO, additionalProjectIds);
        List<Long> deptIdFilter = queryDTO.getDeptId() == null ? null : this.deptService.getDeptIdListByParentId(queryDTO.getDeptId());
        IPage<Project> projectPage = this.projectDao.selectPageByDeptIdListAndProjectIdList(queryDTO, deptIdFilter, projectIdFilter);
        List<ProjectVO> projectList = this.buildProjectVOList(projectPage.getRecords());
        return ProjectServiceImpl.toPagingData(projectList, projectPage);
    }

    private List<Long> resolveProjectIdFilter(ProjectQueryDTO queryDTO, List<Long> additionalProjectIds) {
        boolean hasChargeUser = StringUtils.hasText((String)queryDTO.getChargeUsername());
        List<Long> validAdditionalIds = this.normalizeIds(additionalProjectIds);
        if (!hasChargeUser && validAdditionalIds.isEmpty()) {
            return null;
        }
        LinkedHashSet<Long> projectIdSet = new LinkedHashSet<Long>();
        if (hasChargeUser) {
            List<Long> userIdList = this.userService.searchUserIds(queryDTO.getChargeUsername().trim());
            List<Long> matchedProjectIds = this.userProjectService.getProjectIdListByUserIdList(userIdList);
            projectIdSet.addAll(this.normalizeIds(matchedProjectIds));
        }
        projectIdSet.addAll(validAdditionalIds);
        return new ArrayList<Long>(projectIdSet);
    }

    private List<ProjectVO> buildProjectVOList(List<Project> projectList) {
        if (CollectionUtils.isEmpty(projectList)) {
            return new ArrayList<ProjectVO>();
        }
        List<Long> projectIdList = projectList.stream().map(BaseEntity::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<UserProject> userProjectList = this.userProjectService.lisUserProjectByProjectIds(projectIdList);
        Map<Long, Map<Integer, Set<Long>>> relationMap = this.buildProjectUserRelationMap(userProjectList);
        List<Long> userIdList = this.getAllRelationUserIds(userProjectList);
        List<UserBriefVO> userList = this.userService.getUserBriefListByUserIds(userIdList);
        Map<Long, UserBriefVO> userMap = this.buildUserBriefMap(userList);
        Map<Long, Dept> deptMap = this.deptService.getAllDeptMap();
        ArrayList<ProjectVO> resultList = new ArrayList<ProjectVO>(projectList.size());
        for (Project project : projectList) {
            ProjectVO projectVO = CopyBeanUtil.copy(project, ProjectVO.class);
            if (projectVO == null) continue;
            projectVO.setUserList(this.resolveUserBriefList(project.getId(), ProjectUserCode.NORMAL.getType(), relationMap, userMap));
            projectVO.setOwnerList(this.resolveUserBriefList(project.getId(), ProjectUserCode.OWNER.getType(), relationMap, userMap));
            projectVO.setDeptList(this.deptService.getDeptBriefListFromDeptMapByChildId(deptMap, project.getDeptId()));
            projectVO.setCreateTime(project.getCreateTime());
            resultList.add(projectVO);
        }
        return resultList;
    }

    private Map<Long, Map<Integer, Set<Long>>> buildProjectUserRelationMap(List<UserProject> userProjectList) {
        HashMap<Long, Map<Integer, Set<Long>>> result = new HashMap<Long, Map<Integer, Set<Long>>>();
        if (CollectionUtils.isEmpty(userProjectList)) {
            return result;
        }
        for (UserProject relation : userProjectList) {
            if (relation == null || relation.getProjectId() == null || relation.getUserId() == null || relation.getUserType() == null) continue;
            result.computeIfAbsent(relation.getProjectId(), key -> new HashMap()).computeIfAbsent(relation.getUserType(), key -> new LinkedHashSet()).add(relation.getUserId());
        }
        return result;
    }

    private List<Long> getAllRelationUserIds(List<UserProject> userProjectList) {
        if (CollectionUtils.isEmpty(userProjectList)) {
            return new ArrayList<Long>();
        }
        return userProjectList.stream().filter(Objects::nonNull).map(UserProject::getUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private Set<Long> getProjectUserIds(Long projectId, Integer userType, Map<Long, Map<Integer, Set<Long>>> relationMap) {
        Map<Integer, Set<Long>> userTypeMap = relationMap.get(projectId);
        if (userTypeMap == null) {
            return Collections.emptySet();
        }
        return userTypeMap.getOrDefault(userType, Collections.emptySet());
    }

    private List<UserBriefVO> resolveUserBriefList(Long projectId, Integer userType, Map<Long, Map<Integer, Set<Long>>> relationMap, Map<Long, UserBriefVO> userMap) {
        Set<Long> userIds = this.getProjectUserIds(projectId, userType, relationMap);
        ArrayList<UserBriefVO> result = new ArrayList<UserBriefVO>(userIds.size());
        for (Long userId : userIds) {
            UserBriefVO user = userMap.get(userId);
            if (user == null) continue;
            result.add(user);
        }
        return result;
    }

    private List<UserBasicVO> resolveUserBasicList(Long projectId, Integer userType, Map<Long, Map<Integer, Set<Long>>> relationMap, Map<Long, UserBasicVO> userMap) {
        Set<Long> userIds = this.getProjectUserIds(projectId, userType, relationMap);
        ArrayList<UserBasicVO> result = new ArrayList<UserBasicVO>(userIds.size());
        for (Long userId : userIds) {
            UserBasicVO user = userMap.get(userId);
            if (user == null) continue;
            result.add(user);
        }
        return result;
    }

    private Map<Long, UserBriefVO> buildUserBriefMap(List<UserBriefVO> userList) {
        HashMap<Long, UserBriefVO> result = new HashMap<Long, UserBriefVO>();
        if (CollectionUtils.isEmpty(userList)) {
            return result;
        }
        for (UserBriefVO user : userList) {
            if (user == null || user.getId() == null) continue;
            result.put(user.getId(), user);
        }
        return result;
    }

    private Map<Long, UserBasicVO> buildUserBasicMap(List<UserBasicVO> userList) {
        HashMap<Long, UserBasicVO> result = new HashMap<Long, UserBasicVO>();
        if (CollectionUtils.isEmpty(userList)) {
            return result;
        }
        for (UserBasicVO user : userList) {
            if (user == null || user.getId() == null) continue;
            result.put(user.getId(), user);
        }
        return result;
    }

    private Project getRequiredProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("\u9879\u76ee ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        Project project = this.projectDao.selectByProjectId(projectId);
        if (project == null) {
            throw new YakSecurityException(ResultCode.PROJECT_NOT_EXISTS);
        }
        return project;
    }

    private void checkParam(ProjectSaveDTO projectSaveDTO, boolean update) {
        int duplicateCount;
        if (projectSaveDTO == null) {
            throw new IllegalArgumentException("\u9879\u76ee\u4fe1\u606f\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (!StringUtils.hasText((String)projectSaveDTO.getProjectName())) {
            throw new YakSecurityException(ResultCode.PROJECT_NAME_CANNOT_BE_BLANK);
        }
        projectSaveDTO.setProjectName(projectSaveDTO.getProjectName().trim());
        Long excludeProjectId = null;
        if (update) {
            if (projectSaveDTO.getId() == null) {
                throw new IllegalArgumentException("\u9879\u76ee ID \u4e0d\u80fd\u4e3a\u7a7a");
            }
            excludeProjectId = projectSaveDTO.getId();
        }
        if ((duplicateCount = this.projectDao.selectCountByProjectNameAndNotProjectId(projectSaveDTO.getProjectName(), excludeProjectId)) > 0) {
            throw new YakSecurityException(ResultCode.PROJECT_NAME_ALREADY_EXISTS);
        }
    }

    private List<String> listResourceOfProject(Long projectId) {
        if (projectId == null) {
            return new ArrayList<String>();
        }
        Project project = this.projectDao.selectByProjectId(projectId);
        if (project == null) {
            return new ArrayList<String>();
        }
        List<ResourceDTO> resourceList = this.resourceExtend.getResourceList(projectId, null);
        if (CollectionUtils.isEmpty(resourceList)) {
            return new ArrayList<String>();
        }
        return resourceList.stream().filter(Objects::nonNull).map(ResourceDTO::getResourceName).filter(StringUtils::hasText).collect(Collectors.toList());
    }

    private void saveRelationOplog(String operator, String operation, Project project, String content) {
        this.oplogService.saveOplog(new OplogDTO(operator, operation, OPERATION_OBJECT_PROJECT, project.getProjectName(), content));
    }

    private void checkUserId(Long userId) {
        if (userId == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
    }

    private static <T> PagingData<T> toPagingData(List<T> records, IPage<?> page) {
        return PagingData.from((PageData)new PageData(records, page.getTotal(), page.getPages(), page.getCurrent(), page.getSize()));
    }

    private String generateProjectCode() {
        return PROJECT_CODE_PREFIX + MathUtil.getRandomNumber(7);
    }

    private List<Long> normalizeIds(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return new ArrayList<Long>();
        }
        return idList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}

