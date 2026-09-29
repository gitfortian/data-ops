package io.yak.ops.business.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Spring 调度以驱动 {@code AgentTurnDispatcher} 的 QUEUED 周期扫描。
 * 对齐仓库既有惯例（lineage / offline / realtime 同模式）；模块开关关闭时整体不装配，
 * 不影响宿主上下文的其它调度行为。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAgentEnabled
@EnableScheduling
public class AgentSchedulingConfiguration {
}
