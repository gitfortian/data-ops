/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 *  org.springframework.context.annotation.Configuration
 *  org.springframework.context.annotation.Import
 */
package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.controller.v1.OplogController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="yak.security", name={"database-enabled", "web-enabled", "audit-enabled"}, havingValue="true", matchIfMissing=true)
@Import(value={OplogController.class})
public class YakSecurityAuditConfiguration {
}

