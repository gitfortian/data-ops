/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.PagingData
 *  org.springframework.beans.factory.annotation.Qualifier
 *  org.springframework.context.annotation.Primary
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.common.PagingData;
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
import io.yak.framework.security.common.entity.UserResource;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.enums.resource.ControlLevelCode;
import io.yak.framework.security.common.vo.resource.MByRDataVO;
import io.yak.framework.security.common.vo.resource.MByRVO;
import io.yak.framework.security.common.vo.resource.MByUDataVO;
import io.yak.framework.security.common.vo.resource.MByUVO;
import io.yak.framework.security.dao.UserResourceDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.extend.ResourceExtend;
import io.yak.framework.security.service.ResourceTypeService;
import io.yak.framework.security.service.UserResourceService;
import java.lang.invoke.CallSite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service(value="yakSecurityResourceAuthorizationService")
@Primary
public class ResourceAuthorizationService
implements UserResourceService {
    private static final Long SYSTEM_USER_ID = 0L;
    private static final Long SYSTEM_SCOPE_ID = 0L;
    private final UserResourceService delegate;
    private final UserResourceDao userResourceDao;
    private final ResourceTypeService resourceTypeService;
    private final ResourceExtend resourceExtend;

    public ResourceAuthorizationService(@Qualifier(value="yakSecurityUserResourceServiceImpl") UserResourceService delegate, UserResourceDao userResourceDao, ResourceTypeService resourceTypeService, ResourceExtend resourceExtend) {
        this.delegate = delegate;
        this.userResourceDao = userResourceDao;
        this.resourceTypeService = resourceTypeService;
        this.resourceExtend = resourceExtend;
    }

    @Override
    public int getResourceCntByUserId(Long userId, UserResourceQueryDTO queryDTO) {
        return this.delegate.getResourceCntByUserId(userId, queryDTO);
    }

    @Override
    public PagingData<MByRVO> getManageByResourcePage(MByRQueryDTO queryDTO) {
        return this.delegate.getManageByResourcePage(queryDTO);
    }

    @Override
    public PagingData<MByUVO> getManageByUserPage(MByUQueryDTO queryDTO) {
        return this.delegate.getManageByUserPage(queryDTO);
    }

    @Override
    public List<MByUDataVO> getManagerByUserDataList(MByUDataQueryDTO queryDTO) {
        return this.delegate.getManagerByUserDataList(queryDTO);
    }

    @Override
    public List<MByRDataVO> getManagerByResourceDataList(MByRDataQueryDTO queryDTO) {
        return this.delegate.getManagerByResourceDataList(queryDTO);
    }

    @Override
    public ControlLevelCode getControlLevel(ControlLevelQueryDTO queryDTO) {
        return this.delegate.getControlLevel(queryDTO);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void assignResourcePermission(AssignToOneUserDTO assignDTO) {
        this.validate(assignDTO);
        Long userId = assignDTO.getUserId();
        Long projectId = assignDTO.getProjectId();
        Long resourceTypeId = assignDTO.getResourceTypeId();
        int controlLevel = assignDTO.getControlLevel();
        UserResourceQueryDTO scope = new UserResourceQueryDTO(controlLevel, projectId, resourceTypeId);
        List<Long> partialIds = this.normalizeIds(assignDTO.getExcludeIdList());
        if (partialIds.isEmpty()) {
            this.userResourceDao.deleteByUserId(userId, scope);
        } else if (projectId == null) {
            this.userResourceDao.deleteByUserIdWithoutProjectIdList(userId, scope, partialIds);
        } else if (resourceTypeId == null) {
            this.userResourceDao.deleteByUserIdWithoutResourceTypeIdList(userId, scope, partialIds);
        }
        List<ResourceDTO> resources = this.resolveResourcesForIds(projectId, resourceTypeId, this.normalizeIds(assignDTO.getIdList()));
        this.insertRelations(Collections.singletonList(userId), resources, controlLevel);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void assignResourcePermission(AssignToManyUserDTO assignDTO) {
        this.validate(assignDTO);
        Long projectId = assignDTO.getProjectId();
        Long resourceTypeId = assignDTO.getResourceTypeId();
        Long resourceId = assignDTO.getResourceId();
        int controlLevel = assignDTO.getControlLevel();
        UserResourceQueryDTO scope = new UserResourceQueryDTO(controlLevel, projectId, resourceTypeId, resourceId);
        this.userResourceDao.deleteWithoutUserIdList(scope, this.normalizeIds(assignDTO.getExcludeUserIdList()));
        List<Long> userIds = this.normalizeIds(assignDTO.getUserIdList());
        if (userIds.isEmpty()) {
            return;
        }
        this.insertRelations(userIds, this.resolveScopeResources(projectId, resourceTypeId, resourceId), controlLevel);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void batchAssignResourcePermission(BatchAssignDTO assignDTO) {
        this.validate(assignDTO);
        List<Long> userIds = this.normalizeIds(assignDTO.getUserIdList());
        List<Long> ids = this.normalizeIds(assignDTO.getIdList());
        int controlLevel = assignDTO.getControlLevel();
        Long projectId = assignDTO.getProjectId();
        Long resourceTypeId = assignDTO.getResourceTypeId();
        UserResourceQueryDTO scope = new UserResourceQueryDTO(controlLevel, projectId, resourceTypeId);
        if (Boolean.TRUE.equals(assignDTO.getAssignFlag())) {
            this.deleteByScopeIds(projectId, resourceTypeId, ids, scope);
        } else {
            this.userResourceDao.deleteByUserIdList(userIds, scope);
        }
        if (userIds.isEmpty() || ids.isEmpty()) {
            return;
        }
        this.insertRelations(userIds, this.resolveResourcesForIds(projectId, resourceTypeId, ids), controlLevel);
    }

    @Override
    public boolean getViewPermissionControlStatus() {
        return this.userResourceDao.selectCountByUserId(SYSTEM_USER_ID, this.viewControlQuery()) > 0;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void setViewPermissionControlStatus(boolean enabled) {
        boolean current = this.getViewPermissionControlStatus();
        if (current == enabled) {
            return;
        }
        if (!enabled) {
            this.userResourceDao.deleteByUserId(SYSTEM_USER_ID, this.viewControlQuery());
            return;
        }
        this.userResourceDao.deleteByControlLevel(ControlLevelCode.VIEW);
        UserResource marker = new UserResource();
        marker.setUserId(SYSTEM_USER_ID);
        marker.setProjectId(SYSTEM_SCOPE_ID);
        marker.setResourceTypeId(SYSTEM_SCOPE_ID);
        marker.setResourceId(SYSTEM_SCOPE_ID);
        marker.setControlLevel(ControlLevelCode.NONE.getType());
        this.userResourceDao.insert(marker);
    }

    @Override
    public void changeResourceViewControlStatus() {
        this.setViewPermissionControlStatus(!this.getViewPermissionControlStatus());
    }

    private UserResourceQueryDTO viewControlQuery() {
        return UserResourceQueryDTO.getOpenViewPermissionControlQueryEntity();
    }

    private void deleteByScopeIds(Long projectId, Long resourceTypeId, List<Long> ids, UserResourceQueryDTO scope) {
        if (ids.isEmpty()) {
            return;
        }
        if (projectId == null) {
            this.userResourceDao.deleteByProjectIdList(ids, scope);
        } else if (resourceTypeId == null) {
            this.userResourceDao.deleteByResourceTypeIdList(ids, scope);
        } else {
            this.userResourceDao.deleteByResourceIdList(ids, scope);
        }
    }

    private List<ResourceDTO> resolveResourcesForIds(Long projectId, Long resourceTypeId, List<Long> ids) {
        if (ids.isEmpty()) {
            return new ArrayList<ResourceDTO>();
        }
        ArrayList<ResourceDTO> resources = new ArrayList<ResourceDTO>();
        if (projectId == null) {
            List<Long> typeIds = this.normalizeIds(this.resourceTypeService.getAllResourceTypeIdList());
            for (Long selectedProjectId : ids) {
                for (Long typeId : typeIds) {
                    resources.addAll(this.resourcesForType(selectedProjectId, typeId));
                }
            }
            return this.distinctResources(resources);
        }
        if (resourceTypeId == null) {
            for (Long selectedTypeId : ids) {
                resources.addAll(this.resourcesForType(projectId, selectedTypeId));
            }
            return this.distinctResources(resources);
        }
        LinkedHashSet<Long> selectedResourceIds = new LinkedHashSet<Long>(ids);
        for (ResourceDTO resource : this.resourcesForType(projectId, resourceTypeId)) {
            if (!selectedResourceIds.contains(resource.getResourceId())) continue;
            resources.add(resource);
        }
        return this.distinctResources(resources);
    }

    private List<ResourceDTO> resolveScopeResources(Long projectId, Long resourceTypeId, Long resourceId) {
        if (resourceId != null) {
            List<ResourceDTO> matches = this.resourcesForType(projectId, resourceTypeId).stream().filter(resource -> Objects.equals(resource.getResourceId(), resourceId)).collect(Collectors.toList());
            if (matches.isEmpty()) {
                throw new IllegalArgumentException("\u8d44\u6e90\u4e0d\u5b58\u5728\u6216\u4e0d\u5c5e\u4e8e\u5f53\u524d\u9879\u76ee\u548c\u8d44\u6e90\u7c7b\u578b");
            }
            return matches;
        }
        if (resourceTypeId != null) {
            return this.resourcesForType(projectId, resourceTypeId);
        }
        ArrayList<ResourceDTO> resources = new ArrayList<ResourceDTO>();
        for (Long typeId : this.normalizeIds(this.resourceTypeService.getAllResourceTypeIdList())) {
            resources.addAll(this.resourcesForType(projectId, typeId));
        }
        return this.distinctResources(resources);
    }

    private List<ResourceDTO> resourcesForType(Long projectId, Long resourceTypeId) {
        if (projectId == null || resourceTypeId == null) {
            return new ArrayList<ResourceDTO>();
        }
        List<ResourceDTO> source = this.resourceExtend.getResourceList(projectId, resourceTypeId);
        if (CollectionUtils.isEmpty(source)) {
            return new ArrayList<ResourceDTO>();
        }
        ArrayList<ResourceDTO> result = new ArrayList<ResourceDTO>();
        for (ResourceDTO resource : source) {
            if (resource == null || resource.getResourceId() == null) continue;
            ResourceDTO normalized = new ResourceDTO();
            normalized.setProjectId(resource.getProjectId() == null ? projectId : resource.getProjectId());
            normalized.setResourceTypeId(resource.getResourceTypeId() == null ? resourceTypeId : resource.getResourceTypeId());
            normalized.setResourceId(resource.getResourceId());
            normalized.setResourceName(resource.getResourceName());
            result.add(normalized);
        }
        return this.distinctResources(result);
    }

    private List<ResourceDTO> distinctResources(List<ResourceDTO> resources) {
        LinkedHashMap<CallSite, ResourceDTO> distinct = new LinkedHashMap<CallSite, ResourceDTO>();
        if (resources == null) {
            return new ArrayList<ResourceDTO>();
        }
        for (ResourceDTO resource : resources) {
            if (resource == null || resource.getProjectId() == null || resource.getResourceTypeId() == null || resource.getResourceId() == null) continue;
            String key = resource.getProjectId() + ":" + resource.getResourceTypeId() + ":" + resource.getResourceId();
            distinct.putIfAbsent((CallSite)((Object)key), resource);
        }
        return new ArrayList<ResourceDTO>(distinct.values());
    }

    private void insertRelations(List<Long> userIds, List<ResourceDTO> resources, int controlLevel) {
        List<Long> normalizedUserIds = this.normalizeIds(userIds);
        List<ResourceDTO> normalizedResources = this.distinctResources(resources);
        if (normalizedUserIds.isEmpty() || normalizedResources.isEmpty()) {
            return;
        }
        LinkedHashMap<CallSite, UserResource> relations = new LinkedHashMap<CallSite, UserResource>();
        for (Long userId : normalizedUserIds) {
            for (ResourceDTO resource : normalizedResources) {
                UserResource relation = new UserResource();
                relation.setUserId(userId);
                relation.setProjectId(resource.getProjectId());
                relation.setResourceTypeId(resource.getResourceTypeId());
                relation.setResourceId(resource.getResourceId());
                relation.setControlLevel(controlLevel);
                String key = userId + ":" + resource.getProjectId() + ":" + resource.getResourceTypeId() + ":" + resource.getResourceId() + ":" + controlLevel;
                relations.putIfAbsent((CallSite)((Object)key), relation);
            }
        }
        this.userResourceDao.insertBatch(new ArrayList<UserResource>(relations.values()));
    }

    private void validate(AssignToOneUserDTO assignDTO) {
        if (assignDTO == null) {
            throw new IllegalArgumentException("\u5355\u7528\u6237\u8d44\u6e90\u5206\u914d\u53c2\u6570\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (assignDTO.getUserId() == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        this.validateHierarchy(assignDTO.getProjectId(), assignDTO.getResourceTypeId(), null);
        this.validateControlLevel(assignDTO.getControlLevel());
    }

    private void validate(AssignToManyUserDTO assignDTO) {
        if (assignDTO == null) {
            throw new IllegalArgumentException("\u591a\u7528\u6237\u8d44\u6e90\u5206\u914d\u53c2\u6570\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (assignDTO.getProjectId() == null) {
            throw new YakSecurityException(ResultCode.PROJECT_ID_CANNOT_BE_NULL);
        }
        this.validateHierarchy(assignDTO.getProjectId(), assignDTO.getResourceTypeId(), assignDTO.getResourceId());
        this.validateControlLevel(assignDTO.getControlLevel());
    }

    private void validate(BatchAssignDTO assignDTO) {
        if (assignDTO == null) {
            throw new IllegalArgumentException("\u6279\u91cf\u8d44\u6e90\u5206\u914d\u53c2\u6570\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (assignDTO.getUserIdList() == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        if (assignDTO.getAssignFlag() == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_ASSIGN_BATCH_FLAG_CANNOT_BE_NULL);
        }
        this.validateHierarchy(assignDTO.getProjectId(), assignDTO.getResourceTypeId(), null);
        this.validateControlLevel(assignDTO.getControlLevel());
    }

    private void validateHierarchy(Long projectId, Long resourceTypeId, Long resourceId) {
        if (resourceTypeId != null && projectId == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_ASSIGN_ERROR_2);
        }
        if (resourceId != null && resourceTypeId == null) {
            throw new YakSecurityException(ResultCode.RESOURCE_ASSIGN_ERROR);
        }
    }

    private void validateControlLevel(Integer controlLevel) {
        if (!Objects.equals(controlLevel, ControlLevelCode.VIEW.getType()) && !Objects.equals(controlLevel, ControlLevelCode.ADMIN.getType())) {
            throw new YakSecurityException(ResultCode.RESOURCE_INVALID_CONTROL_LEVEL);
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return new ArrayList<Long>();
        }
        return ids.stream().filter(Objects::nonNull).filter(id -> id > 0L).distinct().collect(Collectors.toList());
    }
}

