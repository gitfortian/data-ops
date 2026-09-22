/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.PagingData
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.project.ProjectBriefQueryDTO;
import io.yak.framework.security.common.dto.resource.AssignToManyUserDTO;
import io.yak.framework.security.common.dto.resource.AssignToOneUserDTO;
import io.yak.framework.security.common.dto.resource.BatchAssignDTO;
import io.yak.framework.security.common.dto.resource.ControlLevelQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUQueryDTO;
import io.yak.framework.security.common.dto.resource.ResourceDTO;
import io.yak.framework.security.common.dto.resource.UserResourceQueryDTO;
import io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO;
import io.yak.framework.security.common.dto.user.UserBriefQueryDTO;
import io.yak.framework.security.common.entity.UserResource;
import io.yak.framework.security.common.entity.dept.Dept;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.enums.resource.ControlLevelCode;
import io.yak.framework.security.common.enums.resource.HasLevelCode;
import io.yak.framework.security.common.enums.resource.ShowLevelCode;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.resource.MByRDataVO;
import io.yak.framework.security.common.vo.resource.MByRVO;
import io.yak.framework.security.common.vo.resource.MByUDataVO;
import io.yak.framework.security.common.vo.resource.MByUVO;
import io.yak.framework.security.common.vo.resource.ResourceTypeVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.dao.UserResourceDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.extend.ResourceExtend;
import io.yak.framework.security.service.DeptService;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.service.ResourceTypeService;
import io.yak.framework.security.service.UserResourceService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service(value="yakSecurityUserResourceServiceImpl")
public class UserResourceServiceImpl
implements UserResourceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserResourceServiceImpl.class);
    private static final Long SYSTEM_USER_ID = 0L;
    private final UserResourceDao userResourceDao;
    private final DeptService deptService;
    private final UserService userService;
    private final ProjectService projectService;
    private final ResourceTypeService resourceTypeService;
    private final ResourceExtend resourceExtend;

    public UserResourceServiceImpl(UserResourceDao userResourceDao, DeptService deptService, UserService userService, ProjectService projectService, ResourceTypeService resourceTypeService, ResourceExtend resourceExtend) {
        this.userResourceDao = userResourceDao;
        this.deptService = deptService;
        this.userService = userService;
        this.projectService = projectService;
        this.resourceTypeService = resourceTypeService;
        this.resourceExtend = resourceExtend;
    }

    @Override
    public int getResourceCntByUserId(Long userId, UserResourceQueryDTO queryDTO) {
        if (userId == null || queryDTO == null) {
            return 0;
        }
        return this.userResourceDao.selectCountByUserId(userId, queryDTO);
    }

    @Override
    public boolean getViewPermissionControlStatus() {
        UserResourceQueryDTO queryDTO = UserResourceQueryDTO.getOpenViewPermissionControlQueryEntity();
        return this.userResourceDao.selectCountByUserId(SYSTEM_USER_ID, queryDTO) > 0;
    }

    @Override
    public List<MByUDataVO> getManagerByUserDataList(MByUDataQueryDTO queryDTO) {
        this.checkParam(queryDTO);
        Long projectId = queryDTO.getProjectId();
        Long resourceTypeId = queryDTO.getResourceTypeId();
        int showLevel = queryDTO.getShowLevel();
        int controlLevel = queryDTO.getControlLevel();
        Long userId = queryDTO.getUserId();
        boolean batch = Boolean.TRUE.equals(queryDTO.getBatch());
        ArrayList<MByUDataVO> resultList = new ArrayList<MByUDataVO>();
        if (Objects.equals(ShowLevelCode.PROJECT.getType(), showLevel)) {
            this.buildProjectPermissionData(resultList, batch, controlLevel, userId);
            return resultList;
        }
        if (Objects.equals(ShowLevelCode.RESOURCE_TYPE.getType(), showLevel)) {
            this.buildResourceTypePermissionData(resultList, batch, controlLevel, userId, projectId);
            return resultList;
        }
        this.buildResourcePermissionData(resultList, batch, controlLevel, userId, projectId, resourceTypeId);
        return resultList;
    }

    @Override
    public List<MByRDataVO> getManagerByResourceDataList(MByRDataQueryDTO queryDTO) {
        this.checkParam(queryDTO);
        List<UserBriefVO> userList = this.userService.getAllUserBriefListOrderByCreateTime(false);
        if (CollectionUtils.isEmpty(userList)) {
            return new ArrayList<MByRDataVO>();
        }
        boolean batch = Boolean.TRUE.equals(queryDTO.getBatch());
        ArrayList<MByRDataVO> resultList = new ArrayList<MByRDataVO>(userList.size());
        for (UserBriefVO user : userList) {
            MByRDataVO dataVO = new MByRDataVO();
            dataVO.setUserId(user.getId());
            dataVO.setUserName(user.getUserName());
            dataVO.setRealName(user.getRealName());
            HasLevelCode hasLevel = this.getHasLevel(batch, queryDTO.getControlLevel(), user.getId(), queryDTO.getProjectId(), queryDTO.getResourceTypeId(), queryDTO.getResourceId());
            dataVO.setHasLevel(hasLevel.getType());
            resultList.add(dataVO);
        }
        return resultList;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void changeResourceViewControlStatus() {
        boolean enabled = this.getViewPermissionControlStatus();
        if (enabled) {
            UserResourceQueryDTO queryDTO = UserResourceQueryDTO.getOpenViewPermissionControlQueryEntity();
            this.userResourceDao.deleteByUserId(SYSTEM_USER_ID, queryDTO);
            LOGGER.info("\u5173\u95ed\u8d44\u6e90\u67e5\u770b\u6743\u9650\u63a7\u5236");
            return;
        }
        this.userResourceDao.deleteByControlLevel(ControlLevelCode.VIEW);
        UserResource controlResource = new UserResource();
        controlResource.setUserId(SYSTEM_USER_ID);
        controlResource.setControlLevel(ControlLevelCode.VIEW.getType());
        this.userResourceDao.insert(controlResource);
        LOGGER.info("\u5f00\u542f\u8d44\u6e90\u67e5\u770b\u6743\u9650\u63a7\u5236");
    }

    @Override
    public ControlLevelCode getControlLevel(ControlLevelQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u6743\u9650\u63a7\u5236\u7ea7\u522b\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (queryDTO.getUserId() == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        if (queryDTO.getProjectId() == null) {
            throw new YakSecurityException(ResultCode.PROJECT_ID_CANNOT_BE_NULL);
        }
        if (queryDTO.getResourceTypeId() == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_TYPE_ID_CANNOT_BE_NULL);
        }
        if (queryDTO.getResourceId() == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_ID_CANNOT_BE_NULL);
        }
        Integer controlLevel = this.userResourceDao.selectControlLevel(queryDTO);
        if (controlLevel == null) {
            return this.getViewPermissionControlStatus() ? ControlLevelCode.NONE : ControlLevelCode.VIEW;
        }
        ControlLevelCode controlLevelCode = ControlLevelCode.getByType(controlLevel);
        if (controlLevelCode == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_INVALID_CONTROL_LEVEL);
        }
        return controlLevelCode;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void assignResourcePermission(AssignToOneUserDTO assignDTO) {
        this.checkParam(assignDTO);
        Long userId = assignDTO.getUserId();
        Long projectId = assignDTO.getProjectId();
        Long resourceTypeId = assignDTO.getResourceTypeId();
        int controlLevel = assignDTO.getControlLevel();
        UserResourceQueryDTO queryDTO = new UserResourceQueryDTO(controlLevel, projectId, resourceTypeId);
        List<Long> excludeIdList = this.normalizeIds(assignDTO.getExcludeIdList());
        if (excludeIdList.isEmpty()) {
            this.userResourceDao.deleteByUserId(userId, queryDTO);
        } else if (projectId == null) {
            this.userResourceDao.deleteByUserIdWithoutProjectIdList(userId, queryDTO, excludeIdList);
        } else if (resourceTypeId == null) {
            this.userResourceDao.deleteByUserIdWithoutResourceTypeIdList(userId, queryDTO, excludeIdList);
        }
        List<Long> idList = this.normalizeIds(assignDTO.getIdList());
        if (idList.isEmpty()) {
            return;
        }
        List<UserResource> userResourceList = this.getUserResourceList(projectId, resourceTypeId, controlLevel, idList, Collections.singletonList(userId));
        this.insertBatch(userResourceList);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void assignResourcePermission(AssignToManyUserDTO assignDTO) {
        this.checkParam(assignDTO);
        List<Long> userIdList = this.normalizeIds(assignDTO.getUserIdList());
        Long projectId = assignDTO.getProjectId();
        Long resourceTypeId = assignDTO.getResourceTypeId();
        Long resourceId = assignDTO.getResourceId();
        int controlLevel = assignDTO.getControlLevel();
        UserResourceQueryDTO queryDTO = new UserResourceQueryDTO(controlLevel, projectId, resourceTypeId, resourceId);
        this.userResourceDao.deleteWithoutUserIdList(queryDTO, this.normalizeIds(assignDTO.getExcludeUserIdList()));
        if (userIdList.isEmpty()) {
            return;
        }
        ArrayList<ResourceDTO> resourceList = new ArrayList<ResourceDTO>();
        if (resourceId == null) {
            ResourceExtend resourceExtend = this.resourceExtend;
            List<ResourceDTO> extensionResources = resourceExtend.getResourceList(projectId, resourceTypeId);
            if (!CollectionUtils.isEmpty(extensionResources)) {
                resourceList.addAll(extensionResources);
            }
        } else {
            resourceList.add(new ResourceDTO(projectId, resourceTypeId, resourceId));
        }
        List<UserResource> userResourceList = this.buildUserResourceList(controlLevel, userIdList, resourceList);
        this.insertBatch(userResourceList);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void batchAssignResourcePermission(BatchAssignDTO assignDTO) {
        this.checkParam(assignDTO);
        List<Long> userIdList = this.normalizeIds(assignDTO.getUserIdList());
        List<Long> idList = this.normalizeIds(assignDTO.getIdList());
        int controlLevel = assignDTO.getControlLevel();
        boolean assignFlag = Boolean.TRUE.equals(assignDTO.getAssignFlag());
        Long projectId = assignDTO.getProjectId();
        Long resourceTypeId = assignDTO.getResourceTypeId();
        this.deleteOldRelationBeforeBatchAssign(projectId, resourceTypeId, assignFlag, controlLevel, idList);
        if (idList.isEmpty() || userIdList.isEmpty()) {
            return;
        }
        List<UserResource> userResourceList = this.getUserResourceList(projectId, resourceTypeId, controlLevel, idList, userIdList);
        this.insertBatch(userResourceList);
    }

    @Override
    public PagingData<MByUVO> getManageByUserPage(MByUQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u7528\u6237\u6743\u9650\u5206\u9875\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        Map<Long, Dept> deptMap = this.deptService.getAllDeptMap();
        PagingData<UserBriefVO> userPage = this.userService.getUserBriefPage(new UserBriefQueryDTO(queryDTO));
        if (userPage == null || CollectionUtils.isEmpty((Collection)userPage.getBizData())) {
            return new PagingData(new ArrayList(), userPage == null ? null : userPage.getPagination());
        }
        boolean viewControlEnabled = this.getViewPermissionControlStatus();
        ArrayList<MByUVO> resultList = new ArrayList<MByUVO>(userPage.getBizData().size());
        for (UserBriefVO user : userPage.getBizData()) {
            MByUVO dataVO = CopyBeanUtil.copy(user, MByUVO.class);
            if (dataVO == null) continue;
            dataVO.setUserId(user.getId());
            dataVO.setDeptList(this.deptService.getDeptBriefListFromDeptMapByChildId(deptMap, user.getDeptId()));
            dataVO.setAdminResourceCnt(this.userResourceDao.selectCountByUserIdAndControlLevel(user.getId(), ControlLevelCode.ADMIN));
            if (viewControlEnabled) {
                dataVO.setViewResourceCnt(this.userResourceDao.selectCountByUserIdAndControlLevel(user.getId(), ControlLevelCode.VIEW));
            }
            resultList.add(dataVO);
        }
        return new PagingData(resultList, userPage.getPagination());
    }

    @Override
    public PagingData<MByRVO> getManageByResourcePage(MByRQueryDTO queryDTO) {
        this.checkParam(queryDTO);
        boolean viewControlEnabled = this.getViewPermissionControlStatus();
        Integer showLevel = queryDTO.getShowLevel();
        if (Objects.equals(showLevel, ShowLevelCode.PROJECT.getType())) {
            return this.dealProjectLevel(queryDTO, viewControlEnabled);
        }
        if (Objects.equals(showLevel, ShowLevelCode.RESOURCE_TYPE.getType())) {
            return this.dealResourceTypeLevel(queryDTO, viewControlEnabled);
        }
        return this.dealResourceLevel(queryDTO, viewControlEnabled);
    }

    private void buildProjectPermissionData(List<MByUDataVO> resultList, boolean batch, int controlLevel, Long userId) {
        List<ProjectBriefVO> projectList = this.projectService.getProjectBriefList();
        if (CollectionUtils.isEmpty(projectList)) {
            return;
        }
        for (ProjectBriefVO project : projectList) {
            MByUDataVO dataVO = new MByUDataVO(project.getId(), project.getProjectName());
            HasLevelCode hasLevel = this.getHasLevel(batch, controlLevel, userId, project.getId(), null, null);
            dataVO.setHasLevel(hasLevel.getType());
            resultList.add(dataVO);
        }
    }

    private void buildResourceTypePermissionData(List<MByUDataVO> resultList, boolean batch, int controlLevel, Long userId, Long projectId) {
        List<ResourceTypeVO> resourceTypeList = this.resourceTypeService.getAllResourceTypeList();
        if (CollectionUtils.isEmpty(resourceTypeList)) {
            return;
        }
        for (ResourceTypeVO resourceType : resourceTypeList) {
            MByUDataVO dataVO = new MByUDataVO(resourceType.getId(), resourceType.getTypeName());
            HasLevelCode hasLevel = this.getHasLevel(batch, controlLevel, userId, projectId, resourceType.getId(), null);
            dataVO.setHasLevel(hasLevel.getType());
            resultList.add(dataVO);
        }
    }

    private void buildResourcePermissionData(List<MByUDataVO> resultList, boolean batch, int controlLevel, Long userId, Long projectId, Long resourceTypeId) {
        ResourceExtend resourceExtend = this.resourceExtend;
        List<ResourceDTO> resourceList = resourceExtend.getResourceList(projectId, resourceTypeId);
        if (CollectionUtils.isEmpty(resourceList)) {
            return;
        }
        for (ResourceDTO resource : resourceList) {
            MByUDataVO dataVO = new MByUDataVO(resource.getResourceId(), resource.getResourceName());
            HasLevelCode hasLevel = this.getHasLevel(batch, controlLevel, userId, projectId, resourceTypeId, resource.getResourceId());
            dataVO.setHasLevel(hasLevel.getType());
            resultList.add(dataVO);
        }
    }

    private HasLevelCode getHasLevel(boolean batch, int controlLevel, Long userId, Long projectId, Long resourceTypeId, Long resourceId) {
        if (batch) {
            return HasLevelCode.NONE;
        }
        UserResourceQueryDTO queryDTO = new UserResourceQueryDTO(controlLevel, projectId, resourceTypeId, resourceId);
        int assignedResourceCount = this.getResourceCntByUserId(userId, queryDTO);
        if (assignedResourceCount <= 0) {
            return HasLevelCode.NONE;
        }
        if (resourceId != null) {
            return HasLevelCode.ALL;
        }
        int totalResourceCount = this.resourceExtend.getResourceCnt(projectId, resourceTypeId);
        if (totalResourceCount <= 0) {
            return HasLevelCode.NONE;
        }
        return assignedResourceCount >= totalResourceCount ? HasLevelCode.ALL : HasLevelCode.HALF;
    }

    private List<UserResource> getUserResourceList(Long projectId, Long resourceTypeId, int controlLevel, List<Long> idList, List<Long> userIdList) {
        List<Long> resourceTypeIdList;
        List<Long> projectIdList;
        List<Long> validIds = this.normalizeIds(idList);
        List<Long> validUserIds = this.normalizeIds(userIdList);
        if (validIds.isEmpty() || validUserIds.isEmpty()) {
            return new ArrayList<UserResource>();
        }
        ArrayList<Long> resourceIdList = null;
        if (projectId == null) {
            projectIdList = new ArrayList<Long>(validIds);
            resourceTypeIdList = this.normalizeIds(this.resourceTypeService.getAllResourceTypeIdList());
        } else if (resourceTypeId == null) {
            projectIdList = Collections.singletonList(projectId);
            resourceTypeIdList = new ArrayList<Long>(validIds);
        } else {
            projectIdList = Collections.singletonList(projectId);
            resourceTypeIdList = Collections.singletonList(resourceTypeId);
            resourceIdList = new ArrayList<Long>(validIds);
        }
        List<ResourceDTO> resourceList = this.getResourceDTOList(projectIdList, resourceTypeIdList, resourceIdList);
        return this.buildUserResourceList(controlLevel, validUserIds, resourceList);
    }

    private List<ResourceDTO> getResourceDTOList(List<Long> projectIdList, List<Long> resourceTypeIdList, List<Long> resourceIdList) {
        if (CollectionUtils.isEmpty(projectIdList) || CollectionUtils.isEmpty(resourceTypeIdList)) {
            return new ArrayList<ResourceDTO>();
        }
        ArrayList<ResourceDTO> resourceList = new ArrayList<ResourceDTO>();
        ResourceExtend resourceExtend = this.resourceExtend;
        for (Long projectId : projectIdList) {
            if (projectId == null) continue;
            for (Long resourceTypeId : resourceTypeIdList) {
                if (resourceTypeId == null) continue;
                if (resourceIdList == null) {
                    List<ResourceDTO> extensionResources = resourceExtend.getResourceList(projectId, resourceTypeId);
                    if (CollectionUtils.isEmpty(extensionResources)) continue;
                    resourceList.addAll(extensionResources);
                    continue;
                }
                for (Long resourceId : resourceIdList) {
                    if (resourceId == null) continue;
                    resourceList.add(new ResourceDTO(projectId, resourceTypeId, resourceId));
                }
            }
        }
        return resourceList;
    }

    private List<UserResource> buildUserResourceList(int controlLevel, List<Long> userIdList, List<ResourceDTO> resourceList) {
        if (CollectionUtils.isEmpty(userIdList) || CollectionUtils.isEmpty(resourceList)) {
            return new ArrayList<UserResource>();
        }
        ArrayList<UserResource> userResourceList = new ArrayList<UserResource>(userIdList.size() * resourceList.size());
        for (Long userId : userIdList) {
            if (userId == null) continue;
            for (ResourceDTO resource : resourceList) {
                if (resource == null) continue;
                UserResource userResource = new UserResource();
                userResource.setUserId(userId);
                userResource.setControlLevel(controlLevel);
                userResourceList.add(userResource);
            }
        }
        return userResourceList;
    }

    private void deleteOldRelationBeforeBatchAssign(Long projectId, Long resourceTypeId, boolean assignFlag, int controlLevel, List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return;
        }
        UserResourceQueryDTO queryDTO = new UserResourceQueryDTO(controlLevel, projectId, resourceTypeId);
        if (!assignFlag) {
            this.userResourceDao.deleteByUserIdList(idList, queryDTO);
            return;
        }
        if (projectId == null) {
            this.userResourceDao.deleteByProjectIdList(idList, queryDTO);
        } else if (resourceTypeId == null) {
            this.userResourceDao.deleteByResourceTypeIdList(idList, queryDTO);
        } else {
            this.userResourceDao.deleteByResourceIdList(idList, queryDTO);
        }
    }

    private int getAdminOrViewUserCnt(UserResourceQueryDTO queryDTO) {
        List<Long> userIdList = this.userResourceDao.selectUserIdListGroupByUserId(queryDTO);
        if (CollectionUtils.isEmpty(userIdList)) {
            return 0;
        }
        int totalResourceCount = this.resourceExtend.getResourceCnt(queryDTO.getProjectId(), queryDTO.getResourceTypeId());
        if (totalResourceCount <= 0) {
            return 0;
        }
        int result = 0;
        for (Long userId : userIdList) {
            int assignedResourceCount = this.userResourceDao.selectCountByUserId(userId, queryDTO);
            if (assignedResourceCount < totalResourceCount) continue;
            ++result;
        }
        return result;
    }

    private PagingData<MByRVO> dealProjectLevel(MByRQueryDTO queryDTO, boolean viewControlEnabled) {
        PagingData<ProjectBriefVO> projectPage = this.projectService.getProjectBriefPage(new ProjectBriefQueryDTO(queryDTO));
        if (projectPage == null || CollectionUtils.isEmpty((Collection)projectPage.getBizData())) {
            return new PagingData(new ArrayList(), projectPage == null ? null : projectPage.getPagination());
        }
        ArrayList<MByRVO> resultList = new ArrayList<MByRVO>(projectPage.getBizData().size());
        for (ProjectBriefVO project : projectPage.getBizData()) {
            MByRVO dataVO = new MByRVO();
            dataVO.setProjectId(project.getId());
            dataVO.setProjectCode(project.getProjectCode());
            dataVO.setProjectName(project.getProjectName());
            UserResourceQueryDTO adminQuery = new UserResourceQueryDTO(ControlLevelCode.ADMIN.getType(), project.getId());
            dataVO.setAdminUserCnt(this.getAdminOrViewUserCnt(adminQuery));
            if (viewControlEnabled) {
                UserResourceQueryDTO viewQuery = new UserResourceQueryDTO(ControlLevelCode.VIEW.getType(), project.getId());
                dataVO.setViewUserCnt(this.getAdminOrViewUserCnt(viewQuery));
            }
            resultList.add(dataVO);
        }
        return new PagingData(resultList, projectPage.getPagination());
    }

    private PagingData<MByRVO> dealResourceTypeLevel(MByRQueryDTO queryDTO, boolean viewControlEnabled) {
        PagingData<ResourceTypeVO> resourceTypePage = this.resourceTypeService.getResourceTypePage(new ResourceTypeQueryDTO(queryDTO));
        if (resourceTypePage == null || CollectionUtils.isEmpty((Collection)resourceTypePage.getBizData())) {
            return new PagingData(new ArrayList(), resourceTypePage == null ? null : resourceTypePage.getPagination());
        }
        ProjectBriefVO project = this.projectService.getProjectBriefByProjectId(queryDTO.getProjectId());
        ArrayList<MByRVO> resultList = new ArrayList<MByRVO>(resourceTypePage.getBizData().size());
        for (ResourceTypeVO resourceType : resourceTypePage.getBizData()) {
            MByRVO dataVO = new MByRVO();
            dataVO.setProjectId(queryDTO.getProjectId());
            if (project != null) {
                dataVO.setProjectName(project.getProjectName());
            }
            dataVO.setResourceTypeId(resourceType.getId());
            dataVO.setResourceTypeName(resourceType.getTypeName());
            UserResourceQueryDTO adminQuery = new UserResourceQueryDTO(ControlLevelCode.ADMIN.getType(), queryDTO.getProjectId(), resourceType.getId());
            dataVO.setAdminUserCnt(this.getAdminOrViewUserCnt(adminQuery));
            if (viewControlEnabled) {
                UserResourceQueryDTO viewQuery = new UserResourceQueryDTO(ControlLevelCode.VIEW.getType(), queryDTO.getProjectId(), resourceType.getId());
                dataVO.setViewUserCnt(this.getAdminOrViewUserCnt(viewQuery));
            }
            resultList.add(dataVO);
        }
        return new PagingData(resultList, resourceTypePage.getPagination());
    }

    private PagingData<MByRVO> dealResourceLevel(MByRQueryDTO queryDTO, boolean viewControlEnabled) {
        PagingData<ResourceDTO> resourcePage = this.resourceExtend.getResourcePage(queryDTO.getProjectId(), queryDTO.getResourceTypeId(), queryDTO.getName(), queryDTO.getPage(), queryDTO.getSize());
        if (resourcePage == null) {
            return new PagingData();
        }
        ResourceTypeVO resourceType = this.resourceTypeService.getResourceTypeByResourceTypeId(queryDTO.getResourceTypeId());
        ArrayList<MByRVO> resultList = new ArrayList<MByRVO>();
        if (CollectionUtils.isEmpty((Collection)resourcePage.getBizData())) {
            return new PagingData(resultList, resourcePage.getPagination());
        }
        for (ResourceDTO resource : resourcePage.getBizData()) {
            MByRVO dataVO = new MByRVO();
            dataVO.setProjectId(queryDTO.getProjectId());
            dataVO.setResourceId(resource.getResourceId());
            dataVO.setResourceName(resource.getResourceName());
            if (resourceType != null) {
                dataVO.setResourceTypeId(resourceType.getId());
                dataVO.setResourceTypeName(resourceType.getTypeName());
            }
            UserResourceQueryDTO adminQuery = new UserResourceQueryDTO(ControlLevelCode.ADMIN.getType(), queryDTO.getProjectId(), queryDTO.getResourceTypeId(), resource.getResourceId());
            dataVO.setAdminUserCnt(this.userResourceDao.selectCountGroupByUserId(adminQuery));
            if (viewControlEnabled) {
                UserResourceQueryDTO viewQuery = new UserResourceQueryDTO(ControlLevelCode.VIEW.getType(), queryDTO.getProjectId(), queryDTO.getResourceTypeId(), resource.getResourceId());
                dataVO.setViewUserCnt(this.userResourceDao.selectCountGroupByUserId(viewQuery));
            }
            resultList.add(dataVO);
        }
        return new PagingData(resultList, resourcePage.getPagination());
    }

    private void checkParam(Integer controlLevel, Long projectId, Long resourceTypeId, Long resourceId) {
        if (projectId == null) {
            throw new YakSecurityException(ResultCode.PROJECT_ID_CANNOT_BE_NULL);
        }
        if (resourceTypeId == null && resourceId != null) {
            throw new YakSecurityException(ResultCode.RESOURCE_ASSIGN_ERROR);
        }
        if (controlLevel == null || ControlLevelCode.getByType(controlLevel) == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_INVALID_CONTROL_LEVEL);
        }
    }

    private void checkParam(MByRDataQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u6309\u8d44\u6e90\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        this.checkParam(queryDTO.getControlLevel(), queryDTO.getProjectId(), queryDTO.getResourceTypeId(), queryDTO.getResourceId());
    }

    private void checkParam(MByUDataQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u6309\u7528\u6237\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (queryDTO.getUserId() == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        if (ControlLevelCode.getByType(queryDTO.getControlLevel()) == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_INVALID_CONTROL_LEVEL);
        }
        this.checkParam(queryDTO.getShowLevel(), queryDTO.getProjectId(), queryDTO.getResourceTypeId());
    }

    private void checkParam(AssignToOneUserDTO assignDTO) {
        if (assignDTO == null) {
            throw new IllegalArgumentException("\u5355\u7528\u6237\u8d44\u6e90\u5206\u914d\u53c2\u6570\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (assignDTO.getUserId() == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        if (ControlLevelCode.getByType(assignDTO.getControlLevel()) == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_INVALID_CONTROL_LEVEL);
        }
        if (assignDTO.getProjectId() == null && assignDTO.getResourceTypeId() != null) {
            throw new YakSecurityException(ResultCode.RESOURCE_ASSIGN_ERROR_2);
        }
    }

    private void checkParam(AssignToManyUserDTO assignDTO) {
        if (assignDTO == null) {
            throw new IllegalArgumentException("\u591a\u7528\u6237\u8d44\u6e90\u5206\u914d\u53c2\u6570\u4e0d\u80fd\u4e3a\u7a7a");
        }
        this.checkParam(assignDTO.getControlLevel(), assignDTO.getProjectId(), assignDTO.getResourceTypeId(), assignDTO.getResourceId());
    }

    private void checkParam(BatchAssignDTO assignDTO) {
        if (assignDTO == null) {
            throw new IllegalArgumentException("\u6279\u91cf\u8d44\u6e90\u5206\u914d\u53c2\u6570\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (assignDTO.getUserIdList() == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        if (assignDTO.getAssignFlag() == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_ASSIGN_BATCH_FLAG_CANNOT_BE_NULL);
        }
        if (assignDTO.getProjectId() == null && assignDTO.getResourceTypeId() != null) {
            throw new YakSecurityException(ResultCode.RESOURCE_ASSIGN_ERROR_2);
        }
        if (ControlLevelCode.getByType(assignDTO.getControlLevel()) == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_INVALID_CONTROL_LEVEL);
        }
    }

    private void checkParam(MByRQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u8d44\u6e90\u6743\u9650\u5206\u9875\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        this.checkParam(queryDTO.getShowLevel(), queryDTO.getProjectId(), queryDTO.getResourceTypeId());
    }

    private void checkParam(Integer showLevel, Long projectId, Long resourceTypeId) {
        ShowLevelCode showLevelCode = ShowLevelCode.getByType(showLevel);
        if (showLevelCode == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_INVALID_SHOW_LEVEL);
        }
        if (showLevel >= ShowLevelCode.RESOURCE_TYPE.getType()) {
            if (projectId == null) {
                throw new YakSecurityException(ResultCode.RESOURCE_SHOW_LEVEL_ERROR);
            }
            ProjectBriefVO project = this.projectService.getProjectBriefByProjectId(projectId);
            if (project == null) {
                throw new YakSecurityException(ResultCode.PROJECT_NOT_EXISTS);
            }
        }
        if (showLevel >= ShowLevelCode.RESOURCE.getType()) {
            if (resourceTypeId == null) {
                throw new YakSecurityException(ResultCode.RESOURCE_SHOW_LEVEL_ERROR_2);
            }
            ResourceTypeVO resourceType = this.resourceTypeService.getResourceTypeByResourceTypeId(resourceTypeId);
            if (resourceType == null) {
                throw new YakSecurityException(ResultCode.RESOURCE_TYPE_NOT_EXISTS);
            }
        }
    }

    private void insertBatch(List<UserResource> userResourceList) {
        if (CollectionUtils.isEmpty(userResourceList)) {
            return;
        }
        this.userResourceDao.insertBatch(userResourceList);
    }

    private List<Long> normalizeIds(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return new ArrayList<Long>();
        }
        return idList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}

