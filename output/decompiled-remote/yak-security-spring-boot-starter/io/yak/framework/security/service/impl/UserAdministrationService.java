/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.ObjectProvider
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.common.dto.user.UserPasswordResetDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.UserPO;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.dao.mapper.UserMapper;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.extend.PasswordEncoder;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserAdministrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserAdministrationService.class);
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 64;
    private final UserDao userDao;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<AuthenticationManager> authenticationManagerProvider;

    public UserAdministrationService(UserDao userDao, UserMapper userMapper, PasswordEncoder passwordEncoder, ObjectProvider<AuthenticationManager> authenticationManagerProvider) {
        this.userDao = userDao;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManagerProvider = authenticationManagerProvider;
    }

    public void validateDelete(Long targetUserId, Long operatorId, String operator) {
        boolean deletingSelfByName;
        if (targetUserId == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        User targetUser = this.userDao.selectByUserId(targetUserId);
        if (targetUser == null) {
            throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        }
        boolean deletingSelfById = operatorId != null && Objects.equals(targetUserId, operatorId);
        boolean bl = deletingSelfByName = StringUtils.hasText((String)operator) && Objects.equals(targetUser.getUserName(), operator);
        if (deletingSelfById || deletingSelfByName) {
            throw new YakSecurityException("\u4e0d\u80fd\u5220\u9664\u5f53\u524d\u767b\u5f55\u7528\u6237");
        }
    }

    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void resetPassword(Long userId, UserPasswordResetDTO request, String operator) {
        String password;
        if (userId == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        String string = password = request == null ? null : request.getPassword();
        if (!StringUtils.hasText((String)password)) {
            throw new YakSecurityException("\u65b0\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (password.length() < 8 || password.length() > 64) {
            throw new YakSecurityException("\u5bc6\u7801\u957f\u5ea6\u5fc5\u987b\u4e3a 8\uff5e64 \u4f4d");
        }
        User user = this.userDao.selectByUserId(userId);
        if (user == null) {
            throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        }
        String encodedPassword = this.passwordEncoder.encode(password);
        int affectedRows = this.userMapper.update(null, (Wrapper)((LambdaUpdateWrapper)Wrappers.lambdaUpdate().eq(BasePO::getId, (Object)userId)).set(UserPO::getPw, (Object)encodedPassword));
        if (affectedRows != 1) {
            throw new YakSecurityException(ResultCode.USER_ACCOUNT_UPDATE_FAIL);
        }
        this.invalidateUserSessions(userId);
        LOGGER.info("\u7ba1\u7406\u5458\u91cd\u7f6e\u7528\u6237\u5bc6\u7801\u6210\u529f\uff0c\u7528\u6237ID={}\uff0c\u7528\u6237\u540d={}\uff0c\u64cd\u4f5c\u4eba={}", new Object[]{userId, user.getUserName(), operator});
    }

    public void invalidateSessionsAfterPasswordChange(String username, String operator) {
        if (!StringUtils.hasText((String)username)) {
            return;
        }
        User user = this.userDao.selectByUsername(username);
        if (user == null || user.getId() == null) {
            return;
        }
        this.invalidateUserSessions(user.getId());
        LOGGER.info("\u7528\u6237\u5bc6\u7801\u53d8\u66f4\u540e\u6e05\u7406\u767b\u5f55\u6001\uff0c\u7528\u6237ID={}\uff0c\u7528\u6237\u540d={}\uff0c\u64cd\u4f5c\u4eba={}", new Object[]{user.getId(), user.getUserName(), operator});
    }

    public void forceLogout(Long userId, String operator) {
        if (userId == null) {
            throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        }
        User user = this.userDao.selectByUserId(userId);
        if (user == null) {
            throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        }
        AuthenticationManager authenticationManager = (AuthenticationManager)this.authenticationManagerProvider.getIfAvailable();
        if (authenticationManager == null) {
            throw new YakSecurityException("\u5f53\u524d\u8ba4\u8bc1\u6a21\u5f0f\u4e0d\u652f\u6301\u8d26\u53f7\u7ea7\u5f3a\u5236\u4e0b\u7ebf");
        }
        authenticationManager.logoutUser(userId);
        LOGGER.info("\u7ba1\u7406\u5458\u5f3a\u5236\u4e0b\u7ebf\u7528\u6237\uff0c\u7528\u6237ID={}\uff0c\u7528\u6237\u540d={}\uff0c\u64cd\u4f5c\u4eba={}", new Object[]{userId, user.getUserName(), operator});
    }

    private void invalidateUserSessions(Long userId) {
        AuthenticationManager authenticationManager = (AuthenticationManager)this.authenticationManagerProvider.getIfAvailable();
        if (authenticationManager != null) {
            authenticationManager.logoutUser(userId);
        }
    }
}

