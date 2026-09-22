package io.yak.ops.business.metadata.register;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 只为 {@link RegisterRetryWorker} 打开 Spring 原生调度（outbox 轮询不经平台调度器，
 * 形状与 {@code DevelopmentLineageSchedulingConfiguration} 一致）。
 */
@Configuration
@EnableScheduling
class RegisterRetrySchedulingConfiguration {}
