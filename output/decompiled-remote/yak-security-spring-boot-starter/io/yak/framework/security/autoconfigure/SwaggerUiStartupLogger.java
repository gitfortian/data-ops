/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
 *  org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent
 *  org.springframework.context.ApplicationListener
 *  org.springframework.core.env.Environment
 *  org.springframework.util.StringUtils
 *  org.springframework.web.util.UriComponentsBuilder
 */
package io.yak.framework.security.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

final class SwaggerUiStartupLogger
implements ApplicationListener<ServletWebServerInitializedEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SwaggerUiStartupLogger.class);
    private static final String DEFAULT_SWAGGER_UI_PATH = "/swagger-ui.html";
    private final Environment environment;

    SwaggerUiStartupLogger(Environment environment) {
        this.environment = environment;
    }

    public void onApplicationEvent(ServletWebServerInitializedEvent event) {
        ServletWebServerApplicationContext context = event.getApplicationContext();
        if (context.getServerNamespace() != null) {
            return;
        }
        String contextPath = context.getServletContext().getContextPath();
        int port = event.getWebServer().getPort();
        LOGGER.info("Swagger UI: {}", (Object)SwaggerUiStartupLogger.buildSwaggerUiUrl(this.environment, contextPath, port));
    }

    static String buildSwaggerUiUrl(Environment environment, String contextPath, int port) {
        String scheme = (Boolean)environment.getProperty("server.ssl.enabled", Boolean.class, (Object)false) != false ? "https" : "http";
        String host = SwaggerUiStartupLogger.resolveHost(environment.getProperty("server.address"));
        String swaggerUiPath = environment.getProperty("springdoc.swagger-ui.path", DEFAULT_SWAGGER_UI_PATH);
        return UriComponentsBuilder.newInstance().scheme(scheme).host(host).port(port).path(SwaggerUiStartupLogger.normalizePath(contextPath)).path(SwaggerUiStartupLogger.normalizePath(swaggerUiPath)).build().toUriString();
    }

    private static String resolveHost(String configuredAddress) {
        if (!StringUtils.hasText((String)configuredAddress) || "0.0.0.0".equals(configuredAddress) || "::".equals(configuredAddress) || "[::]".equals(configuredAddress)) {
            return "localhost";
        }
        return configuredAddress;
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText((String)path) || "/".equals(path)) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}

