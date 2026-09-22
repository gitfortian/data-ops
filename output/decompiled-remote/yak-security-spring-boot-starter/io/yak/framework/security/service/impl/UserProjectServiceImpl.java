/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.dto.user.UserProjectDTO;
import io.yak.framework.security.common.entity.UserProject;
import io.yak.framework.security.common.enums.project.ProjectUserCode;
import io.yak.framework.security.dao.UserProjectDao;
import io.yak.framework.security.service.PermissionCache;
import io.yak.framework.security.service.UserProjectService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service(value="yakSecurityUserProjectServiceImpl")
public class UserProjectServiceImpl
implements UserProjectService {
    private static final int NORMAL_USER_TYPE = 0;
    private static final int OWNER_USER_TYPE = 1;
    private final UserProjectDao userProjectDao;
    private final PermissionCache permissionCache;

    public UserProjectServiceImpl(UserProjectDao userProjectDao, PermissionCache permissionCache) {
        this.userProjectDao = userProjectDao;
        this.permissionCache = permissionCache;
    }

    @Override
    public List<Long> getUserIdListByProjectId(Long projectId, ProjectUserCode projectUserCode) {
        if (projectId == null || projectUserCode == null) {
            return new ArrayList<Long>();
        }
        ArrayList userIdList = this.userProjectDao.selectUserIdListByProjectId(projectId, projectUserCode.getType());
        return userIdList == null ? new ArrayList() : userIdList;
    }

    @Override
    public List<Long> getProjectIdListByUserIdList(List<Long> userIdList) {
        List<Long> validUserIds = this.normalizeIds(userIdList);
        if (validUserIds.isEmpty()) {
            return new ArrayList<Long>();
        }
        ArrayList projectIdList = this.userProjectDao.selectProjectIdListByUserIdList(validUserIds);
        return projectIdList == null ? new ArrayList() : projectIdList;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void saveUserProject(Long projectId, List<Long> userIdList) {
        this.saveProjectRelation(projectId, userIdList, 0);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void delUserProject(Long projectId, List<Long> userIdList) {
        this.deleteProjectRelation(projectId, userIdList, 0);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void saveOwnerProject(Long projectId, List<Long> ownerIdList) {
        this.saveProjectRelation(projectId, ownerIdList, 1);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void delOwnerProject(Long projectId, List<Long> ownerIdList) {
        this.deleteProjectRelation(projectId, ownerIdList, 1);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateUserProject(Long projectId, List<Long> userIdList) {
        if (projectId == null) {
            return;
        }
        this.deleteUserProjectByProjectId(projectId);
        this.saveUserProject(projectId, userIdList);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateUserInformationAssociatedWithProject(Long projectId, List<Long> userIdList) {
        this.appendMissingProjectRelations(projectId, userIdList, 0);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateOwnerProject(Long projectId, List<Long> ownerIdList) {
        if (projectId == null) {
            return;
        }
        this.deleteOwnerProjectByProjectId(projectId);
        this.saveOwnerProject(projectId, ownerIdList);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateOwnerInformationAssociatedWithProject(Long projectId, List<Long> ownerIdList) {
        this.appendMissingProjectRelations(projectId, ownerIdList, 1);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deleteUserProjectByProjectId(Long projectId) {
        if (projectId == null) {
            return;
        }
        List<Long> affectedUserIds = this.userProjectDao.selectUserIdListByProjectId(projectId, 0);
        this.userProjectDao.deleteByProjectIdAndUserType(projectId, 0);
        this.invalidateUsers(affectedUserIds);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deleteOwnerProjectByProjectId(Long projectId) {
        if (projectId == null) {
            return;
        }
        List<Long> affectedUserIds = this.userProjectDao.selectUserIdListByProjectId(projectId, 1);
        this.userProjectDao.deleteByProjectIdAndUserType(projectId, 1);
        this.invalidateUsers(affectedUserIds);
    }

    @Override
    public List<UserProject> lisUserProjectByProjectIds(List<Long> projectIdList) {
        List<Long> validProjectIds = this.normalizeIds(projectIdList);
        if (validProjectIds.isEmpty()) {
            return new ArrayList<UserProject>();
        }
        ArrayList userProjectList = this.userProjectDao.selectByProjectIds(validProjectIds);
        return userProjectList == null ? new ArrayList() : userProjectList;
    }

    @Override
    public List<UserProject> lisUserProjectByUserProjectDTO(UserProjectDTO userProjectDTO) {
        if (userProjectDTO == null) {
            return new ArrayList<UserProject>();
        }
        ArrayList userProjectList = this.userProjectDao.select(userProjectDTO);
        return userProjectList == null ? new ArrayList() : userProjectList;
    }

    private void saveProjectRelation(Long projectId, List<Long> userIdList, int userType) {
        if (projectId == null) {
            return;
        }
        List<Long> validUserIds = this.normalizeIds(userIdList);
        if (validUserIds.isEmpty()) {
            return;
        }
        List<UserProject> userProjectList = this.buildUserProjectList(projectId, validUserIds, userType);
        this.userProjectDao.insertBatch(userProjectList);
        this.invalidateUsers(validUserIds);
    }

    private void deleteProjectRelation(Long projectId, List<Long> userIdList, int userType) {
        if (projectId == null) {
            return;
        }
        List<Long> validUserIds = this.normalizeIds(userIdList);
        if (validUserIds.isEmpty()) {
            return;
        }
        this.userProjectDao.deleteUserProject(this.buildUserProjectList(projectId, validUserIds, userType));
        this.invalidateUsers(validUserIds);
    }

    private void appendMissingProjectRelations(Long projectId, List<Long> userIdList, int userType) {
        if (projectId == null) {
            return;
        }
        List<Long> validUserIds = this.normalizeIds(userIdList);
        if (validUserIds.isEmpty()) {
            return;
        }
        List<Long> existingUserIds = this.userProjectDao.selectUserIdListByProjectId(projectId, userType);
        HashSet<Long> existingUserIdSet = CollectionUtils.isEmpty(existingUserIds) ? Collections.emptySet() : new HashSet<Long>(existingUserIds);
        List<Long> missingUserIds = validUserIds.stream().filter(userId -> !existingUserIdSet.contains(userId)).collect(Collectors.toList());
        if (missingUserIds.isEmpty()) {
            return;
        }
        this.saveProjectRelation(projectId, missingUserIds, userType);
    }

    private List<UserProject> buildUserProjectList(Long projectId, List<Long> userIdList, int userType) {
        ArrayList<UserProject> userProjectList = new ArrayList<UserProject>(userIdList.size());
        for (Long userId : userIdList) {
            UserProject userProject = new UserProject();
            userProject.setProjectId(projectId);
            userProject.setUserId(userId);
            userProject.setUserType(userType);
            userProjectList.add(userProject);
        }
        return userProjectList;
    }

    private void invalidateUsers(List<Long> userIds) {
        this.normalizeIds(userIds).forEach(this.permissionCache::invalidateUser);
    }

    private List<Long> normalizeIds(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return new ArrayList<Long>();
        }
        return idList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}

