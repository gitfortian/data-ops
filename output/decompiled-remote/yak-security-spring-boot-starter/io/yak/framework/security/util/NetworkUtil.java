/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.servlet.http.HttpServletRequest
 *  org.springframework.web.context.request.RequestAttributes
 *  org.springframework.web.context.request.RequestContextHolder
 *  org.springframework.web.context.request.ServletRequestAttributes
 */
package io.yak.framework.security.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class NetworkUtil {
    private static final String UNKNOWN = "unknown";
    private static final String LOCAL_IPV4 = "127.0.0.1";
    private static final String LOCAL_IPV6 = "0:0:0:0:0:0:0:1";
    private static final String LOCAL_IPV6_SHORT = "::1";
    private static final String IP_SEPARATOR = ",";

    public static String getRealIpAddress() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            throw new IllegalStateException("\u5f53\u524d\u7ebf\u7a0b\u4e0d\u5b58\u5728 HTTP \u8bf7\u6c42\u4e0a\u4e0b\u6587");
        }
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes)requestAttributes;
        return NetworkUtil.getRealIpAddress(servletRequestAttributes.getRequest());
    }

    private static boolean isNotOk(String ipAddress) {
        return ipAddress == null || ipAddress.trim().isEmpty() || UNKNOWN.equalsIgnoreCase(ipAddress.trim());
    }

    public static String getRealIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (NetworkUtil.isNotOk(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (NetworkUtil.isNotOk(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (NetworkUtil.isNotOk(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        if (NetworkUtil.isLocalIpAddress(ipAddress = NetworkUtil.getFirstValidIpAddress(ipAddress))) {
            ipAddress = NetworkUtil.getLocalHostAddress(ipAddress);
        }
        return ipAddress;
    }

    public static String getRealIpAddressOrDefault(String defaultIpAddress) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes)requestAttributes;
            return NetworkUtil.getRealIpAddress(servletRequestAttributes.getRequest());
        }
        return NetworkUtil.isNotOk(defaultIpAddress) ? LOCAL_IPV4 : defaultIpAddress.trim();
    }

    private static String getFirstValidIpAddress(String ipAddress) {
        String[] ipAddressArray;
        if (NetworkUtil.isNotOk(ipAddress)) {
            return ipAddress;
        }
        if (!ipAddress.contains(IP_SEPARATOR)) {
            return ipAddress.trim();
        }
        for (String currentIpAddress : ipAddressArray = ipAddress.split(IP_SEPARATOR)) {
            if (NetworkUtil.isNotOk(currentIpAddress)) continue;
            return currentIpAddress.trim();
        }
        return ipAddress.trim();
    }

    private static boolean isLocalIpAddress(String ipAddress) {
        return LOCAL_IPV4.equals(ipAddress) || LOCAL_IPV6.equals(ipAddress) || LOCAL_IPV6_SHORT.equals(ipAddress);
    }

    private static String getLocalHostAddress(String defaultIpAddress) {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        }
        catch (UnknownHostException exception) {
            return defaultIpAddress;
        }
    }

    private NetworkUtil() {
        throw new IllegalStateException("Utility class");
    }
}

