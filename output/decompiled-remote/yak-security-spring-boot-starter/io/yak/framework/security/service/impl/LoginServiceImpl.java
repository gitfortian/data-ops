/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.Result
 *  jakarta.servlet.http.HttpServletRequest
 *  jakarta.servlet.http.HttpServletResponse
 *  org.springframework.stereotype.Service
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.account.AccountLoginDTO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.extend.LoginExtend;
import io.yak.framework.security.service.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service(value="yakSecurityLoginServiceImpl")
public class LoginServiceImpl
implements LoginService {
    private final LoginExtend loginExtend;

    public LoginServiceImpl(LoginExtend loginExtend) {
        this.loginExtend = Objects.requireNonNull(loginExtend, "loginExtend must not be null");
    }

    @Override
    public UserBriefVO verifyLogin(AccountLoginDTO loginDTO, HttpServletRequest request, HttpServletResponse response) {
        if (loginDTO == null) {
            throw new IllegalArgumentException("\u767b\u5f55\u4fe1\u606f\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (request == null || response == null) {
            throw new IllegalArgumentException("HTTP \u8bf7\u6c42\u548c\u54cd\u5e94\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return this.loginExtend.verifyLogin(loginDTO, request, response);
    }

    @Override
    public Result<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        if (request == null || response == null) {
            throw new IllegalArgumentException("HTTP \u8bf7\u6c42\u548c\u54cd\u5e94\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return this.loginExtend.logout(request, response);
    }

    @Override
    public boolean interceptorCheck(HttpServletRequest request, HttpServletResponse response, String requestMappingValue, List<String> whiteMappingValues) throws IOException {
        if (request == null || response == null) {
            throw new IllegalArgumentException("HTTP \u8bf7\u6c42\u548c\u54cd\u5e94\u4e0d\u80fd\u4e3a\u7a7a");
        }
        List<String> safeWhiteMappingValues = whiteMappingValues == null ? Collections.emptyList() : whiteMappingValues;
        return this.loginExtend.interceptorCheck(request, response, requestMappingValue, safeWhiteMappingValues);
    }
}

