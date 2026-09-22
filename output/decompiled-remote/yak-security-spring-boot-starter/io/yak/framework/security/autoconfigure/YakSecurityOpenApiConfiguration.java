/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.swagger.v3.oas.models.OpenAPI
 *  io.swagger.v3.oas.models.info.Info
 *  io.swagger.v3.oas.models.info.License
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication$Type
 *  org.springframework.context.annotation.Bean
 *  org.springframework.context.annotation.Configuration
 *  org.springframework.core.env.Environment
 */
package io.yak.framework.security.autoconfigure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.yak.framework.security.autoconfigure.SwaggerUiStartupLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix="yak.security", name={"web-enabled"}, havingValue="true", matchIfMissing=true)
public class YakSecurityOpenApiConfiguration {
    @Bean
    @ConditionalOnMissingBean(value={OpenAPI.class})
    public OpenAPI yakSecurityOpenAPI() {
        return new OpenAPI().info(new Info().title("Yak Security API").description("Yak Security HTTP API documentation").version("1.0.0").license(new License().name("Apache License 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }

    @Bean
    @ConditionalOnProperty(prefix="springdoc.swagger-ui", name={"enabled"}, havingValue="true", matchIfMissing=true)
    SwaggerUiStartupLogger swaggerUiStartupLogger(Environment environment) {
        return new SwaggerUiStartupLogger(environment);
    }
}

