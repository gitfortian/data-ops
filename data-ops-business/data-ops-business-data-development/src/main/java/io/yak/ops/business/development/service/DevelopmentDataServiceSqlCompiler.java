package io.yak.ops.business.development.service;

import org.springframework.stereotype.Component;

/**
 * @deprecated Data Service SQL authoring belongs to the {@code dataservice} package.
 *     This Spring wiring shell remains only until the legacy Data Service Node application service
 *     itself is moved without mixing that large mechanical change into Stage 3 hardening.
 */
@Deprecated(forRemoval = false)
@Component
public class DevelopmentDataServiceSqlCompiler
    extends io.yak.ops.business.development.dataservice.DevelopmentDataServiceSqlCompiler {}
