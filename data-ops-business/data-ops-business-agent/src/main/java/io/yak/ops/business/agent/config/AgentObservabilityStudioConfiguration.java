package io.yak.ops.business.agent.config;

import io.agentscope.core.studio.StudioManager;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope Studio 可视化调试接线：通过 WebSocket 将 agent 运行事件推送到 Studio Web UI，
 * 支持实时查看 LLM 调用、工具执行、推理链路等。
 *
 * <p>开关 {@code yak.agent.observability.studio.enabled=true} 时装配；默认关闭。
 * 初始化为异步（Reactor Mono），不阻塞 Spring 上下文启动。
 *
 * <p>销毁时调用 {@link StudioManager#shutdown()} 断开 WebSocket 并释放资源。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAgentEnabled
@ConditionalOnProperty(
    prefix = "yak.agent.observability.studio",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class AgentObservabilityStudioConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AgentObservabilityStudioConfiguration.class);

  private final AgentProperties properties;

  public AgentObservabilityStudioConfiguration(AgentProperties properties) {
    this.properties = properties;
    initStudio();
  }

  private void initStudio() {
    AgentProperties.Observability.Studio studio = properties.getObservability().getStudio();
    String serverUrl = studio.getServerUrl();
    String project = studio.getProject();
    String tracingUrl = studio.getTracingUrl();
    String runName = studio.getRunName();

    log.info("Studio 接线: serverUrl={}, project={}, runName={}", serverUrl, project, runName);

    var builder = StudioManager.init()
        .studioUrl(serverUrl)
        .project(project);

    if (tracingUrl != null && !tracingUrl.isBlank()) {
      builder.tracingUrl(tracingUrl);
    }
    if (runName != null && !runName.isBlank()) {
      builder.runName(runName);
    }

    // 同步完成初始化：AgentRuntime 懒组装 ReActAgent 时要能拿到 StudioManager.getClient()
    // 挂 StudioMessageHook；异步初始化会在首次推理前产生竞态（getClient() 仍为 null）。
    // Studio 为显式开启的调试特性，不可达时最多阻塞 5s 并告警，不阻断主流程。
    try {
      builder.initialize().block(java.time.Duration.ofSeconds(5));
      log.info("Studio 初始化完成: serverUrl={}", serverUrl);
    } catch (Exception e) {
      log.warn("Studio 初始化失败（不影响 agent 主流程）: {}", e.getMessage());
    }
  }

  @PreDestroy
  public void shutdown() {
    if (StudioManager.isInitialized()) {
      log.info("Studio 关闭中...");
      StudioManager.shutdown();
    }
  }
}
