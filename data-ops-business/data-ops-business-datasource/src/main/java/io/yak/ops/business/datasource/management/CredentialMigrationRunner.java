package io.yak.ops.business.datasource.management;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.CredentialCipher;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 存量明文凭证的启动补加密(Ticket 05)。
 *
 * <p>仅在密钥已配置时执行：无密钥即明文兼容模式，此时洗数只会把明文原样写回，毫无收益。
 * 事件线程没有 HTTP 头，而 DAO 层硬依赖 CurrentProject，因此逐项目恢复上下文；
 * 单个项目失败只告警，既不阻断启动也不拖累其余项目。
 */
@Slf4j
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class CredentialMigrationRunner {

  private final DataSourceRepository repository;
  private final CredentialCipher credentialCipher;
  private final DataSourceProperties properties;
  private final ProjectContextScope projectScope;

  @EventListener(ApplicationReadyEvent.class)
  public void migratePlainCredentials() {
    if (!properties.getCredential().isMigrateOnStartup()) return;
    if (!credentialCipher.isEnabled()) {
      log.info("未配置数据源凭证密钥，保持明文兼容模式，跳过存量补加密");
      return;
    }
    for (Long projectId : repository.distinctProjectIds()) {
      migrateProject(projectId);
    }
  }

  private void migrateProject(Long projectId) {
    if (projectId == null) return;
    try {
      int upgraded =
          projectScope.call(
              new ProjectContext(projectId, null), repository::encryptStoredCredentials);
      if (upgraded > 0) {
        log.info("项目 {} 补加密数据源凭证 {} 条", projectId, upgraded);
      }
    } catch (RuntimeException failure) {
      log.warn("项目 {} 的数据源凭证补加密失败", projectId, failure);
    }
  }
}
