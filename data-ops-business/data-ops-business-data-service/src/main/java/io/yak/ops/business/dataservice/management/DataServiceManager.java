package io.yak.ops.business.dataservice.management;

import io.yak.ops.business.dataservice.access.DataServiceApiKeyManager;
import io.yak.ops.business.dataservice.access.DataServiceIpAccessManager;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceRepository;
import io.yak.ops.business.dataservice.runtime.DataServiceRuntimePolicyManager;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owns the stable Data Service aggregate lifecycle and cross-cutting delete/invalidation semantics. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceManager {
  private final DataServiceRepository repository;
  private final DataServiceReader reader;
  private final DataServiceApiKeyManager apiKeyManager;
  private final DataServiceIpAccessManager ipAccessManager;
  private final DataServiceRuntimePolicyManager runtimeManager;
  private final CurrentProject currentProject;

  @Transactional
  public DataServiceDefinition savePublished(
      DataServiceDefinition existing, DataServiceSettings settings,
      PublishedRuntimeSnapshot runtime, SourceReference source) {
    ensurePathAvailable(existing == null ? null : existing.id(), settings.path());
    LocalDateTime now = LocalDateTime.now();
    DataServiceDefinition definition;
    if (existing == null) {
      definition = DataServiceDefinition.create(
          currentProject.requireProjectId(),
          settings,
          runtime,
          source,
          RuntimePolicy.defaults(true),
          now);
    } else {
      definition = existing;
      definition.republish(settings, runtime, source, now);
    }
    DataServiceDefinition saved = repository.save(definition);
    runtimeManager.invalidate(saved.id());
    return saved;
  }

  @Transactional
  public DataServiceDefinition updateSettings(Long id, DataServiceSettings settings) {
    DataServiceDefinition definition = reader.require(id);
    ensurePathAvailable(id, settings.path());
    definition.updateSettings(settings, LocalDateTime.now());
    DataServiceDefinition saved = repository.save(definition);
    runtimeManager.invalidate(id);
    return saved;
  }

  @Transactional
  public DataServiceDefinition setEnabled(Long id, boolean enabled) {
    DataServiceDefinition definition = reader.require(id);
    definition.setEnabled(enabled, LocalDateTime.now());
    DataServiceDefinition saved = repository.save(definition);
    if (!enabled) runtimeManager.invalidate(id);
    return saved;
  }

  @Transactional
  public void delete(Long id) {
    reader.require(id);
    apiKeyManager.deleteKeysForApi(id);
    ipAccessManager.deleteForApi(id);
    if (!repository.delete(id)) throw new IllegalArgumentException("数据服务不存在：" + id);
    runtimeManager.remove(id);
  }

  private void ensurePathAvailable(Long id, String path) {
    if (repository.existsByPath(path, id)) throw new IllegalArgumentException("服务路径已存在：" + path);
  }
}
