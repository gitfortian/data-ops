package io.yak.ops.business.metadata.autoingest;

import io.yak.ops.business.metadata.config.ConditionalOnMetadataPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Feature-gated while S2 is in review; enable only with migrated durable table. */
@Configuration
@EnableScheduling
@ConditionalOnMetadataPersistence
@ConditionalOnProperty(prefix = "yak.metadata.auto-ingest", name = "enabled",
    havingValue = "true", matchIfMissing = false)
public class AutoIngestSchedulingConfiguration {}
