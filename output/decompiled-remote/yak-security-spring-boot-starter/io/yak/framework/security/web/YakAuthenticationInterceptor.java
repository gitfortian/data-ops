/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.ErrorCode
 *  io.yak.framework.common.Result
 *  jakarta.servlet.DispatcherType
 *  jakarta.servlet.http.HttpServletRequest
 *  jakarta.servlet.http.HttpServletResponse
 *  org.springframework.core.annotation.AnnotatedElementUtils
 *  org.springframework.web.method.HandlerMethod
 *  org.springframework.web.servlet.HandlerInterceptor
 */
package io.yak.framework.security.web;

import io.yak.framework.common.ErrorCode;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.service.LoginService;
import io.yak.framework.security.service.RbacPermissionService;
import io.yak.framework.security.util.JsonUtils;
import io.yak.framework.security.web.PublicEndpoint;
import io.yak.framework.security.web.RequiresPermission;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.AnnotatedElement;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class YakAuthenticationInterceptor
implements HandlerInterceptor {
    private final LoginService loginService;
    private final YakSecurityProperties properties;
    private final RbacPermissionService permissionService;
    private final CurrentUserProvider currentUserProvider;

    public YakAuthenticationInterceptor(LoginService loginService, YakSecurityProperties properties, RbacPermissionService permissionService, CurrentUserProvider currentUserProvider) {
        this.loginService = loginService;
        this.properties = properties;
        this.permissionService = permissionService;
        this.currentUserProvider = currentUserProvider;
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        boolean authenticated;
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            return true;
        }
        if (!this.properties.isAuthenticationEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod()) || this.isPublicEndpoint(handler)) {
            return true;
        }
        String contextPath = request.getContextPath();
        String requestPath = request.getRequestURI();
        if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        if (!(authenticated = this.loginService.interceptorCheck(request, response, requestPath, this.properties.getPublicPaths()))) {
            return false;
        }
        RequiresPermission required = this.findRequiredPermission(handler);
        if (required == null || this.permissionService.hasPermission(this.currentUserProvider.getCurrentUser(request), required.value())) {
            return true;
        }
        response.setStatus(403);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(JsonUtils.toJson(Result.fail((ErrorCode)ResultCode.NO_PERMISSION)));
        return false;
    }

    private RequiresPermission findRequiredPermission(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return null;
        }
        HandlerMethod method = (HandlerMethod)handler;
        RequiresPermission annotation = (RequiresPermission)AnnotatedElementUtils.findMergedAnnotation((AnnotatedElement)method.getMethod(), RequiresPermission.class);
        return annotation != null ? annotation : (RequiresPermission)AnnotatedElementUtils.findMergedAnnotation((AnnotatedElement)method.getBeanType(), RequiresPermission.class);
    }

    private boolean isPublicEndpoint(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return false;
        }
        HandlerMethod method = (HandlerMethod)handler;
        return AnnotatedElementUtils.hasAnnotation((AnnotatedElement)method.getMethod(), PublicEndpoint.class) || AnnotatedElementUtils.hasAnnotation((AnnotatedElement)method.getBeanType(), PublicEndpoint.class);
    }
}

