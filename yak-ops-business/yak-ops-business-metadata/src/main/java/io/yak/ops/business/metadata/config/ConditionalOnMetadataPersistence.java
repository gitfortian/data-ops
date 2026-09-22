package io.yak.ops.business.metadata.config;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Keeps the datasource enablement contract at the Metadata persistence boundary. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnDataSourceEnabled
public @interface ConditionalOnMetadataPersistence {
}
