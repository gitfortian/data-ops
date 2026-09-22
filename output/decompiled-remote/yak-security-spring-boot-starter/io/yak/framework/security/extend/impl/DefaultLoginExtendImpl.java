/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.ErrorCode
 *  io.yak.framework.common.Result
 *  jakarta.servlet.http.HttpServletRequest
 *  jakarta.servlet.http.HttpServletResponse
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.util.AntPathMatcher
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.extend.impl;

import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.Result;
import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.common.dto.account.AccountLoginDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.extend.LoginExtend;
import io.yak.framework.security.extend.PasswordEncoder;
import io.yak.framework.security.extend.impl.LoginAttemptGuard;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class DefaultLoginExtendImpl
implements LoginExtend {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLoginExtendImpl.class);
    private static final Integer USER_DISABLED_STATUS = 2;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptGuard loginAttemptGuard;
    private final YakSecurityProperties.LoginSecurityProperties loginProperties;

    public DefaultLoginExtendImpl(UserService userService, PasswordEncoder passwordEncoder, YakSecurityProperties properties, AuthenticationManager authenticationManager) {
        this.userService = Objects.requireNonNull(userService, "userService must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.authenticationManager = Objects.requireNonNull(authenticationManager, "authenticationManager must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        this.loginProperties = properties.getLogin();
        this.loginAttemptGuard = new LoginAttemptGuard(this.loginProperties);
    }

    @Override
    public UserBriefVO verifyLogin(AccountLoginDTO loginDTO, HttpServletRequest request, HttpServletResponse response) throws YakSecurityException {
        this.validateLoginParam(loginDTO, request, response);
        String userName = loginDTO.getUserName().trim();
        String remoteAddress = request.getRemoteAddr();
        if (this.loginAttemptGuard.isBlocked(userName, remoteAddress)) {
            throw new YakSecurityException(ResultCode.USER_ACCOUNT_LOCKED);
        }
        User user = this.userService.getUserByUsername(userName);
        if (user == null) {
            this.loginAttemptGuard.recordFailure(userName, remoteAddress);
            throw new YakSecurityException(this.loginProperties.isHideAccountNotFound() ? ResultCode.USER_CREDENTIALS_ERROR : ResultCode.USER_NOT_EXISTS);
        }
        if (USER_DISABLED_STATUS.equals(user.getStatus())) {
            throw new YakSecurityException(ResultCode.USER_ACCOUNT_DISABLE);
        }
        if (!this.passwordEncoder.matches(loginDTO.getPw(), user.getPw())) {
            this.loginAttemptGuard.recordFailure(userName, remoteAddress);
            throw new YakSecurityException(ResultCode.USER_CREDENTIALS_ERROR);
        }
        if (user.getId() == null) {
            LOGGER.error("\u767b\u5f55\u7528\u6237\u7f3a\u5c11\u7528\u6237 ID\uff0cuserName={}", (Object)userName);
            throw new IllegalStateException("Login user id must not be null");
        }
        this.authenticationManager.login(user.getId(), userName);
        this.loginAttemptGuard.recordSuccess(userName, remoteAddress);
        return CopyBeanUtil.copy(user, UserBriefVO.class);
    }

    @Override
    public Result<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");
        this.authenticationManager.logout();
        return Result.success((Object)Boolean.TRUE);
    }

    @Override
    public boolean interceptorCheck(HttpServletRequest request, HttpServletResponse response, String requestPath, List<String> whiteListPatterns) throws IOException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");
        if (!StringUtils.hasText((String)requestPath)) {
            response.setStatus(400);
            return false;
        }
        if (this.isWhiteListPath(requestPath, whiteListPatterns)) {
            return true;
        }
        if (!this.authenticationManager.isLogin()) {
            return this.handleUnauthorized(response);
        }
        Long loginUserId = this.authenticationManager.getLoginUserId();
        String operator = this.authenticationManager.getLoginUsername();
        if (loginUserId == null || !StringUtils.hasText((String)operator)) {
            this.authenticationManager.logout();
            return this.handleUnauthorized(response);
        }
        User user = this.userService.getUserByUsername(operator);
        if (user == null || USER_DISABLED_STATUS.equals(user.getStatus()) || !Objects.equals(loginUserId, user.getId())) {
            LOGGER.warn("\u767b\u5f55\u6001\u5931\u6548\uff0coperator={}, loginUserId={}", (Object)operator, (Object)loginUserId);
            this.authenticationManager.logout();
            return this.handleUnauthorized(response);
        }
        return true;
    }

    private boolean handleUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(JsonUtils.toJson(Result.fail((ErrorCode)ResultCode.USER_NOT_LOGIN)));
        return false;
    }

    private boolean isWhiteListPath(String requestPath, List<String> whiteListPatterns) {
        if (CollectionUtils.isEmpty(whiteListPatterns)) {
            return false;
        }
        for (String pattern : whiteListPatterns) {
            if (!StringUtils.hasText((String)pattern) || !PATH_MATCHER.match(pattern.trim(), requestPath)) continue;
            return true;
        }
        return false;
    }

    private void validateLoginParam(AccountLoginDTO loginDTO, HttpServletRequest request, HttpServletResponse response) {
        if (loginDTO == null || request == null || response == null || !StringUtils.hasText((String)loginDTO.getUserName()) || !StringUtils.hasText((String)loginDTO.getPw())) {
            throw new YakSecurityException(ResultCode.PARAM_NOT_VALID);
        }
    }
}

