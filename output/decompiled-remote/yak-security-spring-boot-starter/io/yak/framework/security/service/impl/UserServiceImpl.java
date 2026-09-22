/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  io.yak.framework.common.ErrorCode
 *  io.yak.framework.common.PageData
 *  io.yak.framework.common.PagingData
 *  io.yak.framework.common.Result
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.user.UserBriefQueryDTO;
import io.yak.framework.security.common.dto.user.UserDTO;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.entity.BaseEntity;
import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.entity.user.UserBrief;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.enums.user.UserCheckType;
import io.yak.framework.security.common.po.UserPO;
import io.yak.framework.security.common.po.UserProjectPO;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.role.AssignInfoVO;
import io.yak.framework.security.common.vo.role.RoleBriefVO;
import io.yak.framework.security.common.vo.user.CurrentUserVO;
import io.yak.framework.security.common.vo.user.UserBasicVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.common.vo.user.UserVO;
import io.yak.framework.security.dao.PermissionDao;
import io.yak.framework.security.dao.ProjectDao;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.dao.UserProjectDao;
import io.yak.framework.security.dao.UserResourceDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.extend.PasswordEncoder;
import io.yak.framework.security.service.DeptService;
import io.yak.framework.security.service.PermissionService;
import io.yak.framework.security.service.RolePermissionService;
import io.yak.framework.security.service.RoleService;
import io.yak.framework.security.service.UserRoleService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityUserServiceImpl")
public class UserServiceImpl
implements UserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final Pattern USER_NAME_PATTERN = Pattern.compile("^[0-9a-zA-Z_]{5,50}$");
    private static final Pattern USER_PHONE_PATTERN = Pattern.compile("^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\\d{8}$");
    private static final Pattern USER_MAIL_PATTERN = Pattern.compile("^\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$");
    private final UserDao userDao;
    private final PermissionService permissionService;
    private final RolePermissionService rolePermissionService;
    private final DeptService deptService;
    private final RoleService roleService;
    private final UserRoleService userRoleService;
    private final UserProjectDao userProjectDao;
    private final UserResourceDao userResourceDao;
    private final ProjectDao projectDao;
    private final PasswordEncoder passwordEncoder;
    private final PermissionDao permissionDao;

    public UserServiceImpl(UserDao userDao, PermissionService permissionService, RolePermissionService rolePermissionService, DeptService deptService, RoleService roleService, UserRoleService userRoleService, UserProjectDao userProjectDao, UserResourceDao userResourceDao, ProjectDao projectDao, PasswordEncoder passwordEncoder, PermissionDao permissionDao) {
        this.userDao = userDao;
        this.permissionService = permissionService;
        this.rolePermissionService = rolePermissionService;
        this.deptService = deptService;
        this.roleService = roleService;
        this.userRoleService = userRoleService;
        this.userProjectDao = userProjectDao;
        this.userResourceDao = userResourceDao;
        this.projectDao = projectDao;
        this.passwordEncoder = passwordEncoder;
        this.permissionDao = permissionDao;
    }

    @Override
    public CurrentUserVO getCurrentUserByUsername(String username) {
        UserBriefVO basic = this.getUserBriefByUsername(username);
        if (basic == null) {
            return null;
        }
        CurrentUserVO currentUser = CopyBeanUtil.copy(basic, CurrentUserVO.class);
        if (currentUser == null) {
            throw new IllegalStateException("\u5f53\u524d\u7528\u6237\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        List<Long> roleIds = this.userRoleService.getRoleIdListByUserId(basic.getId());
        if (CollectionUtils.isEmpty(roleIds)) {
            currentUser.setPermissionCodes(Collections.emptyList());
            return currentUser;
        }
        List<Long> permissionIds = this.rolePermissionService.getPermissionIdListByRoleIdList(roleIds);
        if (CollectionUtils.isEmpty(permissionIds)) {
            currentUser.setPermissionCodes(Collections.emptyList());
            return currentUser;
        }
        HashSet<Long> permissionIdSet = new HashSet<Long>(permissionIds);
        List<String> permissionCodes = this.permissionDao.selectAllAndAscOrderByLevel().stream().filter(permission -> permissionIdSet.contains(permission.getId())).filter(permission -> Boolean.TRUE.equals(permission.getActive())).map(Permission::getPermissionCode).filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        currentUser.setPermissionCodes(permissionCodes);
        return currentUser;
    }

    @Override
    public Result<Void> check(Integer checkType, String checkValue) {
        if (checkType == null) {
            return Result.buildParamIllegal((String)"\u6821\u9a8c\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (Objects.equals(checkType, UserCheckType.USER_NAME.getCode())) {
            return this.userNameCheck(checkValue);
        }
        if (Objects.equals(checkType, UserCheckType.USER_PHONE.getCode())) {
            return this.userPhoneCheck(checkValue);
        }
        if (Objects.equals(checkType, UserCheckType.USER_MAIL.getCode())) {
            return this.userMailCheck(checkValue);
        }
        return Result.buildParamIllegal((String)"\u6821\u9a8c\u7c7b\u578b\u53c2\u6570\u4e0d\u6b63\u786e");
    }

    @Override
    public PagingData<UserVO> getUserPage(UserQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u7528\u6237\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        List<Long> userIdList = null;
        if (queryDTO.getRoleId() != null && CollectionUtils.isEmpty(userIdList = this.userRoleService.getUserIdListByRoleId(queryDTO.getRoleId()))) {
            return PagingData.from((PageData)PageData.empty((long)queryDTO.getPage(), (long)queryDTO.getSize()));
        }
        IPage<User> userPage = this.userDao.selectPageByUserIdList(queryDTO, userIdList);
        List userList = userPage.getRecords();
        if (CollectionUtils.isEmpty((Collection)userList)) {
            return UserServiceImpl.toPagingData(new ArrayList(), userPage);
        }
        List<Long> userIds = userList.stream().map(BaseEntity::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, List<ProjectBriefVO>> userProjectMap = this.buildUserProjectMap(userIds);
        Map<Long, List<RoleBriefVO>> userRoleMap = this.roleService.getRoleBriefListByUserIds(userIds);
        if (userRoleMap == null) {
            userRoleMap = Collections.emptyMap();
        }
        ArrayList<UserVO> userVOList = new ArrayList<UserVO>(userList.size());
        for (User user : userList) {
            UserVO userVO = CopyBeanUtil.copy(user, UserVO.class);
            if (userVO == null) continue;
            userVO.setRoleList(userRoleMap.getOrDefault(user.getId(), Collections.emptyList()));
            userVO.setProjectList(userProjectMap.getOrDefault(user.getId(), Collections.emptyList()));
            userVO.setUpdateTime(user.getUpdateTime());
            userVO.setCreateTime(user.getCreateTime());
            this.privacyProcessing(userVO);
            userVOList.add(userVO);
        }
        return UserServiceImpl.toPagingData(userVOList, userPage);
    }

    @Override
    public PagingData<UserBriefVO> getUserBriefPage(UserBriefQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u7528\u6237\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        List<Long> deptIdList = this.deptService.getDeptIdListByParentIdAndDeptName(queryDTO.getDeptId(), queryDTO.getDeptName());
        IPage<UserBrief> userPage = this.userDao.selectBriefPageByDeptIdList(queryDTO, deptIdList);
        List<UserBriefVO> userList = CopyBeanUtil.copyList(userPage.getRecords(), UserBriefVO.class);
        return UserServiceImpl.toPagingData(userList, userPage);
    }

    @Override
    public UserVO getUserDetailByUserId(Long userId) {
        if (userId == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        User user = this.userDao.selectByUserId(userId);
        if (user == null) {
            throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        }
        UserVO userVO = CopyBeanUtil.copy(user, UserVO.class);
        if (userVO == null) {
            throw new IllegalStateException("\u7528\u6237\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        this.enrichUserRoleAndPermission(userVO);
        Map<Long, List<ProjectBriefVO>> userProjectMap = this.buildUserProjectMap(Collections.singletonList(userId));
        userVO.setProjectList(userProjectMap.getOrDefault(userId, Collections.emptyList()));
        userVO.setUpdateTime(user.getUpdateTime());
        userVO.setCreateTime(user.getCreateTime());
        return userVO;
    }

    @Override
    public List<UserBasicVO> getUserBasicListByUserIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return new ArrayList<UserBasicVO>();
        }
        return CopyBeanUtil.copyList(this.userDao.selectBriefListByUserIdList(userIds), UserBasicVO.class);
    }

    @Override
    public Result<List<UserVO>> getUserDetailsByUserIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Result.success(new ArrayList());
        }
        List<Long> distinctUserIds = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctUserIds.isEmpty()) {
            return Result.success(new ArrayList());
        }
        List userVOList = distinctUserIds.stream().map(this.userDao::selectByUserId).filter(Objects::nonNull).map(user -> {
            UserVO userVO = CopyBeanUtil.copy(user, UserVO.class);
            if (userVO != null) {
                userVO.setUpdateTime(user.getUpdateTime());
                userVO.setCreateTime(user.getCreateTime());
            }
            return userVO;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, List<ProjectBriefVO>> userProjectMap = this.buildUserProjectMap(distinctUserIds);
        for (UserVO userVO : userVOList) {
            this.enrichUserRoleAndPermission(userVO);
            userVO.setProjectList(userProjectMap.getOrDefault(userVO.getId(), Collections.emptyList()));
        }
        return Result.success(userVOList);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public Result<Void> deleteByUserId(Long userId) {
        if (userId == null) {
            return Result.buildParamIllegal((String)"\u7528\u6237 ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        this.userRoleService.deleteByUserIdOrRoleId(userId, null);
        this.userProjectDao.deleteByUserId(userId);
        this.userResourceDao.deleteByUserId(userId, null);
        boolean success = this.userDao.deleteByUserId(userId);
        if (!success) {
            return Result.fail();
        }
        return Result.success();
    }

    @Override
    public UserBriefVO getUserBriefByUsername(String username) {
        if (!StringUtils.hasText((String)username)) {
            return null;
        }
        User user = this.userDao.selectByUsername(username);
        return CopyBeanUtil.copy(user, UserBriefVO.class);
    }

    @Override
    public User getUserByUsername(String username) {
        if (!StringUtils.hasText((String)username)) {
            return null;
        }
        return this.userDao.selectByUsername(username);
    }

    @Override
    public List<UserBriefVO> getUserBriefListByUserIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return new ArrayList<UserBriefVO>();
        }
        List<UserBrief> userBriefList = this.userDao.selectBriefListByUserIdList(userIds);
        List<UserBriefVO> userBriefVOList = CopyBeanUtil.copyList(userBriefList, UserBriefVO.class);
        if (CollectionUtils.isEmpty(userBriefVOList)) {
            return new ArrayList<UserBriefVO>();
        }
        for (UserBriefVO userBriefVO : userBriefVOList) {
            User user = this.userDao.selectByUserId(userBriefVO.getId());
            if (user != null) {
                userBriefVO.setEmail(user.getEmail());
                userBriefVO.setPhone(user.getPhone());
            }
            List<String> roleNameList = this.roleService.getRoleBriefListByUserId(userBriefVO.getId()).stream().map(RoleBriefVO::getRoleName).collect(Collectors.toList());
            userBriefVO.setRoleList(roleNameList);
        }
        return userBriefVOList;
    }

    @Override
    public List<UserBriefVO> searchUserBriefList(String keyword) {
        List<UserBrief> userList = this.userDao.selectBriefListByNameAndDescOrderByCreateTime(keyword);
        return CopyBeanUtil.copyList(userList, UserBriefVO.class);
    }

    @Override
    public List<UserBriefVO> getAllUserBriefListOrderByCreateTime(boolean ascending) {
        List<UserBrief> userList = this.userDao.selectBriefListOrderByCreateTime(ascending);
        return CopyBeanUtil.copyList(userList, UserBriefVO.class);
    }

    @Override
    public List<Long> searchUserIds(String keyword) {
        ArrayList userIds = this.userDao.selectUserIdListByUsernameOrRealName(keyword);
        return userIds == null ? new ArrayList() : userIds;
    }

    @Override
    public List<UserBriefVO> getAllUserBriefList() {
        List<UserBrief> userList = this.userDao.selectAllBriefList();
        return CopyBeanUtil.copyList(userList, UserBriefVO.class);
    }

    @Override
    public List<UserBriefVO> getUserBriefListByDeptId(Long deptId) {
        List<Long> deptIdList = this.deptService.getDeptIdListByParentId(deptId);
        if (CollectionUtils.isEmpty(deptIdList)) {
            return new ArrayList<UserBriefVO>();
        }
        List<UserBrief> userList = this.userDao.selectBriefListByDeptIdList(deptIdList);
        return CopyBeanUtil.copyList(userList, UserBriefVO.class);
    }

    @Override
    public List<AssignInfoVO> getAssignInfoListByUserId(Long userId) {
        if (userId == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        List<RoleBriefVO> roleList = this.roleService.getAllRoleBriefList();
        if (CollectionUtils.isEmpty(roleList)) {
            return new ArrayList<AssignInfoVO>();
        }
        List<Long> assignedRoleIds = this.userRoleService.getRoleIdListByUserId(userId);
        HashSet<Long> assignedRoleIdSet = CollectionUtils.isEmpty(assignedRoleIds) ? Collections.emptySet() : new HashSet<Long>(assignedRoleIds);
        ArrayList<AssignInfoVO> assignInfoList = new ArrayList<AssignInfoVO>(roleList.size());
        for (RoleBriefVO role : roleList) {
            AssignInfoVO assignInfo = new AssignInfoVO();
            assignInfo.setId(role.getId());
            assignInfo.setName(role.getRoleName());
            assignInfo.setHas(assignedRoleIdSet.contains(role.getId()));
            assignInfoList.add(assignInfo);
        }
        return assignInfoList;
    }

    @Override
    public List<UserBriefVO> getUserBriefListByRoleId(Long roleId) {
        if (roleId == null) {
            return new ArrayList<UserBriefVO>();
        }
        List<Long> userIdList = this.userRoleService.getUserIdListByRoleId(roleId);
        if (CollectionUtils.isEmpty(userIdList)) {
            return new ArrayList<UserBriefVO>();
        }
        List<UserBrief> userList = this.userDao.selectBriefListByUserIdList(userIdList);
        return CopyBeanUtil.copyList(userList, UserBriefVO.class);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public Result<Void> addUser(UserDTO userDTO, String operator) {
        Result<Void> checkResult = this.checkUserParam(userDTO, true);
        if (checkResult.failed()) {
            return checkResult;
        }
        if (this.userDao.selectByUsername(userDTO.getUserName()) != null) {
            return Result.fail((ErrorCode)ResultCode.USER_ACCOUNT_ALREADY_EXIST);
        }
        try {
            UserPO userPO = CopyBeanUtil.copy(userDTO, UserPO.class);
            if (userPO == null) {
                throw new IllegalStateException("\u7528\u6237\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
            }
            userPO.setPw(this.passwordEncoder.encode(userDTO.getPw()));
            int affectedRows = this.userDao.addUser(userPO);
            if (affectedRows != 1) {
                return Result.fail((ErrorCode)ResultCode.USER_ACCOUNT_INSERT_FAIL);
            }
            this.userRoleService.updateUserRoleByUserId(userPO.getId(), userDTO.getRoleIds());
            LOGGER.info("\u65b0\u589e\u7528\u6237\u6210\u529f\uff0c\u7528\u6237ID={}\uff0c\u7528\u6237\u540d={}\uff0c\u64cd\u4f5c\u4eba={}", new Object[]{userPO.getId(), userDTO.getUserName(), operator});
            return Result.success();
        }
        catch (Exception exception) {
            LOGGER.error("\u65b0\u589e\u7528\u6237\u5931\u8d25\uff0c\u7528\u6237\u540d={}\uff0c\u64cd\u4f5c\u4eba={}", new Object[]{userDTO.getUserName(), operator, exception});
            throw new YakSecurityException(ResultCode.USER_ACCOUNT_INSERT_FAIL, (Throwable)exception);
        }
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public Result<Void> editUser(UserDTO userDTO, String operator) {
        Result<Void> checkResult = this.checkUserParam(userDTO, false);
        if (checkResult.failed()) {
            return checkResult;
        }
        User currentUser = this.userDao.selectByUsername(userDTO.getUserName());
        if (currentUser == null) {
            return Result.fail((ErrorCode)ResultCode.USER_ACCOUNT_NOT_EXIST);
        }
        try {
            UserPO userPO = CopyBeanUtil.copy(userDTO, UserPO.class);
            if (userPO == null) {
                throw new IllegalStateException("\u7528\u6237\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
            }
            userPO.setId(currentUser.getId());
            if (StringUtils.hasText((String)userDTO.getPw())) {
                userPO.setPw(this.passwordEncoder.encode(userDTO.getPw()));
            } else {
                userPO.setPw(null);
            }
            int affectedRows = this.userDao.editUser(userPO);
            if (affectedRows != 1) {
                return Result.fail((ErrorCode)ResultCode.USER_ACCOUNT_UPDATE_FAIL);
            }
            this.userRoleService.updateUserRoleByUserId(userPO.getId(), userDTO.getRoleIds());
            LOGGER.info("\u7f16\u8f91\u7528\u6237\u6210\u529f\uff0c\u7528\u6237ID={}\uff0c\u7528\u6237\u540d={}\uff0c\u64cd\u4f5c\u4eba={}", new Object[]{userPO.getId(), userDTO.getUserName(), operator});
            return Result.success();
        }
        catch (Exception exception) {
            LOGGER.error("\u7f16\u8f91\u7528\u6237\u5931\u8d25\uff0c\u7528\u6237\u540d={}\uff0c\u64cd\u4f5c\u4eba={}", new Object[]{userDTO.getUserName(), operator, exception});
            throw new YakSecurityException(ResultCode.USER_ACCOUNT_UPDATE_FAIL, (Throwable)exception);
        }
    }

    private void enrichUserRoleAndPermission(UserVO userVO) {
        ArrayList<Long> permissionIds;
        List<RoleBriefVO> roleList = this.roleService.getRoleBriefListByUserId(userVO.getId());
        if (roleList == null) {
            roleList = new ArrayList<RoleBriefVO>();
        }
        userVO.setRoleList(roleList);
        List<Long> roleIds = roleList.stream().map(RoleBriefVO::getId).filter(Objects::nonNull).collect(Collectors.toList());
        List<Object> list = permissionIds = roleIds.isEmpty() ? new ArrayList() : this.rolePermissionService.getPermissionIdListByRoleIdList(roleIds);
        if (permissionIds == null) {
            permissionIds = new ArrayList();
        }
        userVO.setPermissionTreeVO(this.permissionService.buildPermissionTreeWithHas(permissionIds));
    }

    private Map<Long, List<ProjectBriefVO>> buildUserProjectMap(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<UserProjectPO> userProjectList = this.userProjectDao.selectProjectListByUserIdList(userIds);
        if (CollectionUtils.isEmpty(userProjectList)) {
            return Collections.emptyMap();
        }
        List<Long> projectIds = userProjectList.stream().map(UserProjectPO::getProjectId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (projectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ProjectBriefVO> projectList = CopyBeanUtil.copyList(this.projectDao.selectProjectBriefByProjectIds(projectIds), ProjectBriefVO.class);
        if (CollectionUtils.isEmpty(projectList)) {
            return Collections.emptyMap();
        }
        Map projectMap = projectList.stream().filter(Objects::nonNull).filter(project -> project.getId() != null).collect(Collectors.toMap(ProjectBriefVO::getId, project -> project, (first, second) -> first, LinkedHashMap::new));
        HashMap<Long, List<ProjectBriefVO>> userProjectMap = new HashMap<Long, List<ProjectBriefVO>>();
        for (UserProjectPO userProject : userProjectList) {
            ProjectBriefVO project2 = (ProjectBriefVO)projectMap.get(userProject.getProjectId());
            if (project2 == null) continue;
            userProjectMap.computeIfAbsent(userProject.getUserId(), key -> new ArrayList()).add(project2);
        }
        return userProjectMap;
    }

    private void privacyProcessing(UserVO userVO) {
        if (!StringUtils.hasText((String)userVO.getPhone())) {
            return;
        }
        userVO.setPhone(userVO.getPhone().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
    }

    private static <T> PagingData<T> toPagingData(List<T> records, IPage<?> page) {
        return PagingData.from((PageData)new PageData(records, page.getTotal(), page.getPages(), page.getCurrent(), page.getSize()));
    }

    private Result<Void> checkUserParam(UserDTO userDTO, boolean passwordRequired) {
        if (userDTO == null) {
            return Result.buildParamIllegal((String)"\u7528\u6237\u4fe1\u606f\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (!StringUtils.hasText((String)userDTO.getUserName())) {
            return Result.buildParamIllegal((String)"\u7528\u6237\u540d\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (passwordRequired && !StringUtils.hasText((String)userDTO.getPw())) {
            return Result.buildParamIllegal((String)"\u7528\u6237\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return Result.success(null);
    }

    private Result<Void> userNameCheck(String username) {
        if (!StringUtils.hasText((String)username) || !USER_NAME_PATTERN.matcher(username).matches()) {
            return Result.fail((ErrorCode)ResultCode.USER_NAME_FORMAT_ERROR);
        }
        if (this.userDao.selectByUsername(username) != null) {
            return Result.fail((ErrorCode)ResultCode.USER_NAME_EXISTS);
        }
        return Result.success();
    }

    private Result<Void> userPhoneCheck(String phone) {
        if (!StringUtils.hasText((String)phone) || !USER_PHONE_PATTERN.matcher(phone).matches()) {
            return Result.buildParamIllegal((String)"\u624b\u673a\u53f7\u683c\u5f0f\u4e0d\u6b63\u786e");
        }
        if (this.userDao.selectByUserPhone(phone) != null) {
            return Result.fail((ErrorCode)ResultCode.USER_PHONE_EXIST);
        }
        return Result.success();
    }

    private Result<Void> userMailCheck(String email) {
        if (!StringUtils.hasText((String)email) || !USER_MAIL_PATTERN.matcher(email).matches()) {
            return Result.fail((ErrorCode)ResultCode.USER_EMAIL_FORMAT_ERROR);
        }
        if (this.userDao.selectByUserMail(email) != null) {
            return Result.fail((ErrorCode)ResultCode.USER_EMAIL_EXIST);
        }
        return Result.success();
    }
}

