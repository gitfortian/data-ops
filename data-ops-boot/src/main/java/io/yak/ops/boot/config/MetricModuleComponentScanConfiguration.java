package io.yak.ops.boot.config;

import io.yak.ops.business.metric.config.ConditionalOnMetricPersistence;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Adds Metric's Spring components only when its persistence capability is enabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMetricPersistence
@ComponentScan(basePackages = "io.yak.ops.business.metric")
public class MetricModuleComponentScanConfiguration {}
