/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.servlet.http.HttpServletRequest
 *  org.springframework.util.StringUtils
 *  org.springframework.web.context.request.RequestContextHolder
 *  org.springframework.web.context.request.ServletRequestAttributes
 */
package io.yak.framework.security.util;

import io.yak.framework.security.context.YakSecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class HttpRequestUtil {
    public static final String PROJECT_ID = "X-YAK-SECURITY-PROJECT-ID";
    public static final Integer REDIRECT_CODE = 401;
    public static final Integer COOKIE_OR_SESSION_MAX_AGE_UNIT_SEC = 86400;

    private HttpRequestUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static String getHeaderValue(String headerKey) {
        HttpServletRequest request = ((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest();
        return request.getHeader(headerKey);
    }

    public static String getOperator() {
        return YakSecurityContext.getCurrentUsername();
    }

    public static String getOperator(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return YakSecurityContext.getCurrentUsername();
    }

    public static Long getOperatorId(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return YakSecurityContext.getCurrentUserId();
    }

    public static Long getProjectId(HttpServletRequest request, int defaultAppid) {
        String projectIdStr = request.getHeader(PROJECT_ID);
        if (StringUtils.isEmpty((Object)projectIdStr)) {
            return defaultAppid;
        }
        return HttpRequestUtil.strConvertInteger(projectIdStr);
    }

    public static Long getProjectId(HttpServletRequest request) {
        String projectIdStr = request.getHeader(PROJECT_ID);
        if (StringUtils.isEmpty((Object)projectIdStr)) {
            return null;
        }
        return HttpRequestUtil.strConvertInteger(projectIdStr);
    }

    private static Long strConvertInteger(String str) {
        try {
            return StringUtils.isEmpty((Object)str) ? null : Long.valueOf(str);
        }
        catch (Exception ignore) {
            return null;
        }
    }
}

