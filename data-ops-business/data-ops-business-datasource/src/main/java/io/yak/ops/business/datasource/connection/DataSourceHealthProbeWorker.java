package io.yak.ops.business.datasource.connection;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 连接健康定时巡检(Ticket 03):低频遍历全部项目的数据源,复用 testSaved 回写 conn_status,
 * 让 summary 卡/首页数字不再长期失真。
 *
 * <p>第一件事是恢复项目上下文:调度线程没有 HTTP 头,DAO 层硬依赖 CurrentProject;
 * 单个源失败不中断巡检(Tester 已把失败源写为 DISCONNECTED)。
 * AtomicBoolean skip-if-running 对齐仓内无 ShedLock 的现实。
 */
@Slf4j
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceHealthProbeWorker {

  private final DataSourceRepository repository;
  private final DataSourceReader reader;
  private final DataSourceConnectionTester tester;
  private final DataSourceProperties properties;
  private final ProjectContextScope projectScope;

  private final AtomicBoolean running = new AtomicBoolean(false);

  @Scheduled(
      fixedDelayString = "${yak.datasource.health-probe.interval-ms:300000}",
      initialDelayString = "${yak.datasource.health-probe.initial-delay-ms:60000}")
  public void probeAllProjects() {
    if (!properties.getHealthProbe().isEnabled()) return;
    if (!running.compareAndSet(false, true)) {
      log.debug("上一轮数据源健康巡检仍未结束,本轮跳过");
      return;
    }
    try {
      for (Long projectId : repository.distinctProjectIds()) {
        probeProject(projectId);
      }
    } catch (RuntimeException failure) {
      log.warn("数据源健康巡检整体失败", failure);
    } finally {
      running.set(false);
    }
  }

  private void probeProject(Long projectId) {
    if (projectId == null) return;
    try {
      projectScope.run(new ProjectContext(projectId, null), this::probeInProject);
    } catch (RuntimeException failure) {
      // 项目可能已删除/禁用:逐项目隔离,不拖垮其余项目的巡检。
      log.warn("项目 {} 的数据源健康巡检失败", projectId, failure);
    }
  }

  private void probeInProject() {
    for (DataSourceDefinition definition : reader.findAll(null)) {
      try {
        tester.testSaved(definition.getId());
      } catch (RuntimeException failure) {
        log.debug(
            "数据源 {} 探活失败(conn_status 已回写): {}", definition.getId(), failure.getMessage());
      }
    }
  }
}
