/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  io.yak.framework.common.PageData
 *  io.yak.framework.common.PagingData
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.message.MessageDTO;
import io.yak.framework.security.common.dto.oplog.OplogDTO;
import io.yak.framework.security.common.dto.role.RoleAssignDTO;
import io.yak.framework.security.common.dto.role.RoleQueryDTO;
import io.yak.framework.security.common.dto.role.RoleSaveDTO;
import io.yak.framework.security.common.entity.BaseEntity;
import io.yak.framework.security.common.entity.UserRole;
import io.yak.framework.security.common.entity.role.Role;
import io.yak.framework.security.common.entity.role.RoleBrief;
import io.yak.framework.security.common.entity.user.UserBrief;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.enums.message.MessageCode;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import io.yak.framework.security.common.vo.role.AssignInfoVO;
import io.yak.framework.security.common.vo.role.RoleBriefVO;
import io.yak.framework.security.common.vo.role.RoleDeleteCheckVO;
import io.yak.framework.security.common.vo.role.RoleVO;
import io.yak.framework.security.common.vo.user.UserBasicVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.dao.RoleDao;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.MessageService;
import io.yak.framework.security.service.OplogService;
import io.yak.framework.security.service.PermissionService;
import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.service.RoleService;
import io.yak.framework.security.service.UserRoleService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.JsonUtils;
import io.yak.framework.security.util.MathUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityRoleServiceImpl")
public class RoleServiceImpl
implements RoleService {
    private static final String ROLE_CODE_PREFIX = "r";
    private static final int ROLE_CODE_RANDOM_LENGTH = 7;
    private static final String OPERATION_CREATE = "\u65b0\u589e";
    private static final String OPERATION_EDIT = "\u7f16\u8f91";
    private static final String OPERATION_DELETE = "\u5220\u9664";
    private static final String OPERATION_OBJECT_ROLE = "Role";
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private final RoleDao roleDao;
    private final PermissionService permissionService;
    private final MessageService messageService;
    private final OplogService oplogService;
    private final UserDao userDao;
    private final RolePermissionService rolePermissionService;
    private final UserRoleService userRoleService;

    public RoleServiceImpl(RoleDao roleDao, PermissionService permissionService, MessageService messageService, OplogService oplogService, UserDao userDao, RolePermissionService rolePermissionService, UserRoleService userRoleService) {
        this.roleDao = roleDao;
        this.permissionService = permissionService;
        this.messageService = messageService;
        this.oplogService = oplogService;
        this.userDao = userDao;
        this.rolePermissionService = rolePermissionService;
        this.userRoleService = userRoleService;
    }

    @Override
    public RoleBriefVO getRoleBriefByRoleId(Long roleId) {
        if (roleId == null) {
            return null;
        }
        Role role = this.roleDao.selectByRoleId(roleId);
        return CopyBeanUtil.copy(role, RoleBriefVO.class);
    }

    @Override
    public RoleVO getRoleDetailByRoleId(Long roleId) {
        if (roleId == null) {
            return null;
        }
        Role role = this.roleDao.selectByRoleId(roleId);
        if (role == null) {
            return null;
        }
        RoleVO roleVO = CopyBeanUtil.copy(role, RoleVO.class);
        if (roleVO == null) {
            throw new IllegalStateException("\u89d2\u8272\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        PermissionTreeVO permissionTree = this.permissionService.buildPermissionTreeByRoleId(roleId);
        roleVO.setPermissionTreeVO(permissionTree);
        roleVO.setCreateTime(role.getCreateTime());
        roleVO.setUpdateTime(role.getUpdateTime());
        List<Long> userIdList = this.userRoleService.getUserIdListByRoleId(roleId);
        List<UserBriefVO> userList = this.getUserBriefListByUserIds(userIdList);
        ArrayList<String> userNameList = CollectionUtils.isEmpty(userList) ? new ArrayList<String>() : userList.stream().map(UserBriefVO::getUserName).filter(StringUtils::hasText).collect(Collectors.toList());
        roleVO.setAuthedUserCnt(userIdList.size());
        roleVO.setAuthedUsers(userNameList);
        return roleVO;
    }

    @Override
    public PagingData<RoleVO> getRolePage(RoleQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u89d2\u8272\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        IPage<Role> rolePage = this.roleDao.selectPage(queryDTO);
        List roleList = rolePage.getRecords();
        if (CollectionUtils.isEmpty((Collection)roleList)) {
            return RoleServiceImpl.toPagingData(new ArrayList(), rolePage);
        }
        List<Long> roleIdList = roleList.stream().map(BaseEntity::getId).filter(Objects::nonNull).collect(Collectors.toList());
        List<UserRole> userRoleList = this.userRoleService.getByRoleIds(roleIdList);
        if (userRoleList == null) {
            userRoleList = new ArrayList<UserRole>();
        }
        List<Long> userIdList = userRoleList.stream().map(UserRole::getUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<UserBasicVO> userList = this.getUserBasicListByUserIds(userIdList);
        Map<Long, String> userNameMap = this.buildUserNameMap(userList);
        HashMap roleUserIdMap = new HashMap();
        HashMap roleUserNameMap = new HashMap();
        for (UserRole userRole : userRoleList) {
            if (userRole == null || userRole.getRoleId() == null || userRole.getUserId() == null) continue;
            roleUserIdMap.computeIfAbsent(userRole.getRoleId(), key -> new ArrayList()).add(userRole.getUserId());
            String userName = userNameMap.get(userRole.getUserId());
            if (!StringUtils.hasText((String)userName)) continue;
            roleUserNameMap.computeIfAbsent(userRole.getRoleId(), key -> new ArrayList()).add(userName);
        }
        ArrayList<RoleVO> roleVOList = new ArrayList<RoleVO>(roleList.size());
        for (Role role : roleList) {
            RoleVO roleVO = CopyBeanUtil.copy(role, RoleVO.class);
            if (roleVO == null) continue;
            List assignedUserIds = roleUserIdMap.getOrDefault(role.getId(), Collections.emptyList());
            roleVO.setAuthedUserCnt(assignedUserIds.size());
            roleVO.setAuthedUsers(roleUserNameMap.getOrDefault(role.getId(), Collections.emptyList()));
            roleVO.setCreateTime(role.getCreateTime());
            roleVO.setUpdateTime(role.getUpdateTime());
            roleVOList.add(roleVO);
        }
        return RoleServiceImpl.toPagingData(roleVOList, rolePage);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void createRole(RoleSaveDTO roleSaveDTO, String operator) {
        this.checkParam(roleSaveDTO, false);
        Role role = CopyBeanUtil.copy(roleSaveDTO, Role.class);
        if (role == null) {
            throw new IllegalStateException("\u89d2\u8272\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        role.setRoleCode(this.generateRoleCode());
        this.setLastReviser(role, operator);
        this.roleDao.insert(role);
        this.rolePermissionService.saveRolePermission(role.getId(), roleSaveDTO.getPermissionIdList());
        this.oplogService.saveOplog(new OplogDTO(operator, OPERATION_CREATE, OPERATION_OBJECT_ROLE, roleSaveDTO.getRoleName(), JsonUtils.toJson(roleSaveDTO)));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deleteRoleByRoleId(Long roleId, String operator) {
        if (roleId == null) {
            throw new IllegalArgumentException("\u89d2\u8272 ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        Role role = this.roleDao.selectByRoleId(roleId);
        if (role == null) {
            throw new YakSecurityException(ResultCode.ROLE_NOT_EXISTS);
        }
        this.userRoleService.deleteByUserIdOrRoleId(null, roleId);
        this.rolePermissionService.deleteRolePermissionByRoleId(roleId);
        this.roleDao.deleteByRoleId(roleId);
        this.oplogService.saveOplog(new OplogDTO(operator, OPERATION_DELETE, OPERATION_OBJECT_ROLE, role.getRoleName(), JsonUtils.toJson(role)));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void deleteUserFromRole(Long roleId, Long userId, String operator) {
        if (roleId == null || userId == null) {
            throw new IllegalArgumentException("\u89d2\u8272 ID \u548c\u7528\u6237 ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        Role role = this.roleDao.selectByRoleId(roleId);
        if (role == null) {
            throw new YakSecurityException(ResultCode.ROLE_NOT_EXISTS);
        }
        this.userRoleService.deleteByUserIdOrRoleId(userId, roleId);
        Role updateRole = new Role();
        updateRole.setId(roleId);
        this.setLastReviser(updateRole, operator);
        this.roleDao.update(updateRole);
        this.oplogService.saveOplog(new OplogDTO(operator, OPERATION_EDIT, OPERATION_OBJECT_ROLE, role.getRoleName(), "\u4ece\u89d2\u8272\u4e2d\u5220\u9664\u7528\u6237\uff0cuserId=" + userId));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void updateRole(RoleSaveDTO roleSaveDTO, String operator) {
        if (roleSaveDTO == null || roleSaveDTO.getId() == null) {
            throw new IllegalArgumentException("\u89d2\u8272\u4fe1\u606f\u548c\u89d2\u8272 ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        Role currentRole = this.roleDao.selectByRoleId(roleSaveDTO.getId());
        if (currentRole == null) {
            throw new YakSecurityException(ResultCode.ROLE_NOT_EXISTS);
        }
        this.checkParam(roleSaveDTO, true);
        Role role = CopyBeanUtil.copy(roleSaveDTO, Role.class);
        if (role == null) {
            throw new IllegalStateException("\u89d2\u8272\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        this.setLastReviser(role, operator);
        this.roleDao.update(role);
        this.rolePermissionService.updateRolePermission(role.getId(), roleSaveDTO.getPermissionIdList());
        this.oplogService.saveOplog(new OplogDTO(operator, OPERATION_EDIT, OPERATION_OBJECT_ROLE, roleSaveDTO.getRoleName(), JsonUtils.toJson(roleSaveDTO)));
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void assignRoles(RoleAssignDTO assignDTO, String operator) {
        this.checkAssignParam(assignDTO);
        List<Long> newIdList = this.normalizeIds(assignDTO.getIdList());
        assignDTO.setIdList(newIdList);
        if (Boolean.TRUE.equals(assignDTO.getFlag())) {
            this.assignRolesToUser(assignDTO, operator);
            return;
        }
        this.assignUsersToRole(assignDTO, operator);
    }

    private void assignRolesToUser(RoleAssignDTO assignDTO, String operator) {
        Long userId = assignDTO.getId();
        List<Long> oldRoleIdList = this.userRoleService.getRoleIdListByUserId(userId);
        this.userRoleService.updateUserRoleByUserId(userId, assignDTO.getIdList());
        String targetName = this.getUserDisplayName(userId);
        Long oplogId = this.oplogService.saveOplog(new OplogDTO(operator, OPERATION_EDIT, OPERATION_OBJECT_ROLE, targetName, "\u7ed9\u7528\u6237\u5206\u914d\u89d2\u8272\uff0c" + JsonUtils.toJson(assignDTO)));
        this.packAndSaveMessage(oplogId, oldRoleIdList, assignDTO);
    }

    private void assignUsersToRole(RoleAssignDTO assignDTO, String operator) {
        Long roleId = assignDTO.getId();
        Role role = this.roleDao.selectByRoleId(roleId);
        if (role == null) {
            throw new YakSecurityException(ResultCode.ROLE_NOT_EXISTS);
        }
        List<Long> oldUserIdList = this.userRoleService.getUserIdListByRoleId(roleId);
        this.userRoleService.updateUserRoleByRoleId(roleId, assignDTO.getIdList());
        Role updateRole = new Role();
        updateRole.setId(roleId);
        this.setLastReviser(updateRole, operator);
        this.roleDao.update(updateRole);
        Long oplogId = this.oplogService.saveOplog(new OplogDTO(operator, OPERATION_EDIT, OPERATION_OBJECT_ROLE, role.getRoleName(), "\u7ed9\u89d2\u8272\u5206\u914d\u7528\u6237\uff0c" + JsonUtils.toJson(assignDTO)));
        this.packAndSaveMessage(oplogId, oldUserIdList, assignDTO);
    }

    @Override
    public List<RoleBriefVO> getRoleBriefListByRoleName(String roleName) {
        List<RoleBrief> roleList = this.roleDao.selectBriefListByRoleNameAndDescOrderByCreateTime(roleName);
        ArrayList result = CopyBeanUtil.copyList(roleList, RoleBriefVO.class);
        return result == null ? new ArrayList() : result;
    }

    @Override
    public RoleDeleteCheckVO checkBeforeDelete(Long roleId) {
        if (roleId == null) {
            return null;
        }
        RoleDeleteCheckVO checkVO = new RoleDeleteCheckVO();
        checkVO.setRoleId(roleId);
        List<Long> userIdList = this.userRoleService.getUserIdListByRoleId(roleId);
        if (CollectionUtils.isEmpty(userIdList)) {
            return checkVO;
        }
        List<UserBriefVO> userList = this.getUserBriefListByUserIds(userIdList);
        List<String> userNameList = userList.stream().map(UserBriefVO::getUserName).filter(StringUtils::hasText).collect(Collectors.toList());
        checkVO.setUserNameList(userNameList);
        return checkVO;
    }

    @Override
    public List<RoleBriefVO> getAllRoleBriefList() {
        ArrayList roleList = CopyBeanUtil.copyList(this.roleDao.selectAllBrief(), RoleBriefVO.class);
        return roleList == null ? new ArrayList() : roleList;
    }

    @Override
    public List<RoleBriefVO> getRoleBriefListByUserId(Long userId) {
        if (userId == null) {
            return new ArrayList<RoleBriefVO>();
        }
        List<Long> roleIdList = this.userRoleService.getRoleIdListByUserId(userId);
        if (CollectionUtils.isEmpty(roleIdList)) {
            return new ArrayList<RoleBriefVO>();
        }
        ArrayList roleList = CopyBeanUtil.copyList(this.roleDao.selectBriefListByRoleIdList(roleIdList), RoleBriefVO.class);
        return roleList == null ? new ArrayList() : roleList;
    }

    @Override
    public Map<Long, List<RoleBriefVO>> getRoleBriefListByUserIds(List<Long> userIdList) {
        List<Long> validUserIds = this.normalizeIds(userIdList);
        if (validUserIds.isEmpty()) {
            return new HashMap<Long, List<RoleBriefVO>>();
        }
        List<UserRole> userRoleList = this.userRoleService.getRoleIdListByUserIds(validUserIds);
        if (CollectionUtils.isEmpty(userRoleList)) {
            return new HashMap<Long, List<RoleBriefVO>>();
        }
        List<Long> roleIdList = userRoleList.stream().map(UserRole::getRoleId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<RoleBriefVO> roleList = CopyBeanUtil.copyList(this.roleDao.selectBriefListByRoleIdList(roleIdList), RoleBriefVO.class);
        HashMap<Long, RoleBriefVO> roleMap = new HashMap<Long, RoleBriefVO>();
        if (!CollectionUtils.isEmpty(roleList)) {
            for (RoleBriefVO role : roleList) {
                if (role == null || role.getId() == null) continue;
                roleMap.put(role.getId(), role);
            }
        }
        HashMap<Long, List<RoleBriefVO>> result = new HashMap<Long, List<RoleBriefVO>>();
        for (UserRole userRole : userRoleList) {
            RoleBriefVO role;
            if (userRole == null || userRole.getUserId() == null || userRole.getRoleId() == null || (role = (RoleBriefVO)roleMap.get(userRole.getRoleId())) == null) continue;
            result.computeIfAbsent(userRole.getUserId(), key -> new ArrayList()).add(role);
        }
        return result;
    }

    @Override
    public List<AssignInfoVO> getAssignInfoByRoleId(Long roleId) {
        if (roleId == null) {
            return new ArrayList<AssignInfoVO>();
        }
        List<UserBriefVO> userList = this.getAllUserBriefList();
        if (CollectionUtils.isEmpty(userList)) {
            return new ArrayList<AssignInfoVO>();
        }
        HashSet<Long> assignedUserIds = new HashSet<Long>(this.userRoleService.getUserIdListByRoleId(roleId));
        ArrayList<AssignInfoVO> resultList = new ArrayList<AssignInfoVO>(userList.size());
        for (UserBriefVO user : userList) {
            AssignInfoVO assignInfo = new AssignInfoVO();
            assignInfo.setId(user.getId());
            assignInfo.setName(user.getUserName());
            assignInfo.setHas(assignedUserIds.contains(user.getId()));
            resultList.add(assignInfo);
        }
        return resultList;
    }

    private void packAndSaveMessage(Long oplogId, List<Long> oldIdList, RoleAssignDTO assignDTO) {
        List<Long> oldIds = this.normalizeIds(oldIdList);
        List<Long> newIds = this.normalizeIds(assignDTO.getIdList());
        HashSet<Long> oldIdSet = new HashSet<Long>(oldIds);
        HashSet<Long> newIdSet = new HashSet<Long>(newIds);
        List<Long> removeIdList = oldIds.stream().filter(id -> !newIdSet.contains(id)).collect(Collectors.toList());
        List<Long> addIdList = newIds.stream().filter(id -> !oldIdSet.contains(id)).collect(Collectors.toList());
        if (Boolean.TRUE.equals(assignDTO.getFlag())) {
            List<Long> userIds = Collections.singletonList(assignDTO.getId());
            this.saveRoleAssignMessage(oplogId, userIds, removeIdList, userIds, addIdList);
            return;
        }
        List<Long> roleIds = Collections.singletonList(assignDTO.getId());
        this.saveRoleAssignMessage(oplogId, removeIdList, roleIds, addIdList, roleIds);
    }

    private void saveRoleAssignMessage(Long oplogId, List<Long> removeUserIdList, List<Long> removeRoleIdList, List<Long> addUserIdList, List<Long> addRoleIdList) {
        String removeRoleInfo;
        MessageDTO message;
        String addRoleInfo;
        ArrayList<MessageDTO> messageList = new ArrayList<MessageDTO>();
        String time = LocalDateTime.now().format(MESSAGE_TIME_FORMATTER);
        if (!CollectionUtils.isEmpty(addUserIdList) && !CollectionUtils.isEmpty(addRoleIdList) && StringUtils.hasText((String)(addRoleInfo = this.spliceRoleNameByRoleIdList(addRoleIdList)))) {
            for (Long userId : addUserIdList) {
                if (userId == null) continue;
                message = new MessageDTO(userId, oplogId);
                message.setContent(String.format(MessageCode.ROLE_ADD_MESSAGE.getContent(), time, addRoleInfo));
                message.setTitle(MessageCode.ROLE_ADD_MESSAGE.getTitle());
                messageList.add(message);
            }
        }
        if (!CollectionUtils.isEmpty(removeUserIdList) && !CollectionUtils.isEmpty(removeRoleIdList) && StringUtils.hasText((String)(removeRoleInfo = this.spliceRoleNameByRoleIdList(removeRoleIdList)))) {
            for (Long userId : removeUserIdList) {
                if (userId == null) continue;
                message = new MessageDTO(userId, oplogId);
                message.setContent(String.format(MessageCode.ROLE_REMOVE_MESSAGE.getContent(), time, removeRoleInfo));
                message.setTitle(MessageCode.ROLE_REMOVE_MESSAGE.getTitle());
                messageList.add(message);
            }
        }
        if (!messageList.isEmpty()) {
            this.messageService.saveMessages(messageList);
        }
    }

    private String spliceRoleNameByRoleIdList(List<Long> roleIdList) {
        List<Long> validRoleIds = this.normalizeIds(roleIdList);
        if (validRoleIds.isEmpty()) {
            return null;
        }
        List<RoleBrief> roleList = this.roleDao.selectBriefListByRoleIdList(validRoleIds);
        if (CollectionUtils.isEmpty(roleList)) {
            return null;
        }
        return roleList.stream().map(RoleBrief::getRoleName).filter(StringUtils::hasText).collect(Collectors.joining(","));
    }

    private void checkParam(RoleSaveDTO roleSaveDTO, boolean update) {
        int duplicateCount;
        if (roleSaveDTO == null) {
            throw new IllegalArgumentException("\u89d2\u8272\u4fe1\u606f\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (!StringUtils.hasText((String)roleSaveDTO.getRoleName())) {
            throw new YakSecurityException(ResultCode.ROLE_NAME_CANNOT_BE_BLANK);
        }
        if (CollectionUtils.isEmpty(roleSaveDTO.getPermissionIdList())) {
            throw new YakSecurityException(ResultCode.ROLE_PERMISSION_CANNOT_BE_NULL);
        }
        Long excludeRoleId = null;
        if (update) {
            if (roleSaveDTO.getId() == null) {
                throw new IllegalArgumentException("\u89d2\u8272 ID \u4e0d\u80fd\u4e3a\u7a7a");
            }
            excludeRoleId = roleSaveDTO.getId();
        }
        if ((duplicateCount = this.roleDao.selectCountByRoleNameAndNotRoleId(roleSaveDTO.getRoleName(), excludeRoleId)) > 0) {
            throw new YakSecurityException(ResultCode.ROLE_NAME_ALREADY_EXISTS);
        }
    }

    private void checkAssignParam(RoleAssignDTO assignDTO) {
        if (assignDTO == null) {
            throw new IllegalArgumentException("\u89d2\u8272\u5206\u914d\u53c2\u6570\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (assignDTO.getFlag() == null) {
            throw new YakSecurityException(ResultCode.ROLE_ASSIGN_FLAG_IS_NULL);
        }
        if (assignDTO.getId() == null) {
            throw new IllegalArgumentException("\u89d2\u8272\u5206\u914d\u76ee\u6807 ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
    }

    private List<UserBasicVO> getUserBasicListByUserIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return new ArrayList<UserBasicVO>();
        }
        ArrayList users = CopyBeanUtil.copyList(this.userDao.selectBriefListByUserIdList(userIds), UserBasicVO.class);
        return users == null ? new ArrayList() : users;
    }

    private List<UserBriefVO> getUserBriefListByUserIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return new ArrayList<UserBriefVO>();
        }
        ArrayList users = CopyBeanUtil.copyList(this.userDao.selectBriefListByUserIdList(userIds), UserBriefVO.class);
        return users == null ? new ArrayList() : users;
    }

    private List<UserBriefVO> getAllUserBriefList() {
        List<UserBrief> users = this.userDao.selectAllBriefList();
        ArrayList result = CopyBeanUtil.copyList(users, UserBriefVO.class);
        return result == null ? new ArrayList() : result;
    }

    private Map<Long, String> buildUserNameMap(List<UserBasicVO> userList) {
        if (CollectionUtils.isEmpty(userList)) {
            return new HashMap<Long, String>();
        }
        HashMap<Long, String> result = new HashMap<Long, String>();
        for (UserBasicVO user : userList) {
            if (user == null || user.getId() == null) continue;
            result.put(user.getId(), user.getUserName());
        }
        return result;
    }

    private String getUserDisplayName(Long userId) {
        List<UserBriefVO> userList = this.getUserBriefListByUserIds(Collections.singletonList(userId));
        if (CollectionUtils.isEmpty(userList)) {
            return String.valueOf(userId);
        }
        UserBriefVO user = userList.get(0);
        if (StringUtils.hasText((String)user.getRealName())) {
            return user.getRealName();
        }
        if (StringUtils.hasText((String)user.getUserName())) {
            return user.getUserName();
        }
        return String.valueOf(userId);
    }

    private void setLastReviser(Role role, String operator) {
        if (StringUtils.hasText((String)operator)) {
            role.setLastReviser(operator);
        }
    }

    private static <T> PagingData<T> toPagingData(List<T> records, IPage<?> page) {
        return PagingData.from((PageData)new PageData(records, page.getTotal(), page.getPages(), page.getCurrent(), page.getSize()));
    }

    private String generateRoleCode() {
        return ROLE_CODE_PREFIX + MathUtil.getRandomNumber(7);
    }

    private List<Long> normalizeIds(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return new ArrayList<Long>();
        }
        return idList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}

