package io.yak.ops.business.datasource.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 只为数据源模块内的进程内巡检(如连接健康探活)打开 Spring 原生调度,
 * 形状与 metadata 模块的 RegisterRetrySchedulingConfiguration 一致;
 * 不接平台调度器——housekeeping 的可用性不该依赖调度中心。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnDataSourceEnabled
class DataSourceSchedulingConfiguration {}
