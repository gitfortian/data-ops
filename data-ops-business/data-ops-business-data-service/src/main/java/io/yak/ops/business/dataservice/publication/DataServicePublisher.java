package io.yak.ops.business.dataservice.publication;

import io.yak.ops.business.dataservice.documentation.DataServiceDocumentationManager;
import io.yak.ops.business.dataservice.documentation.DocumentationInput;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ResponseFieldDoc;
import io.yak.ops.business.dataservice.execution.DataServiceSqlCompiler;
import io.yak.ops.business.dataservice.management.DataServiceManager;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.SourceContract;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.SourceDescriptor;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServicePublisher {
  private static final int DEFAULT_MAX_ROWS = 1_000;
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private final DataServicePublicationReader publicationReader;
  private final DataServiceSourceRegistry sourceRegistry;
  private final DataServiceReader dataServiceReader;
  private final DataServiceManager manager;
  private final DataServiceViewFactory viewFactory;
  private final DataServiceDocumentationManager documentationManager;
  private final DataServiceSqlCompiler sqlCompiler;

  @Transactional
  public DataServiceView publish(PublishRequest request) {
    if (request == null) throw new IllegalArgumentException("发布配置不能为空");
    DataServicePublicationReader.SourceIdentity identity = publicationReader.normalizeIdentity(request.sourceType(), request.sourceRef());
    DataServiceSourceProvider provider = sourceRegistry.require(identity.sourceType());
    ResolvedSource resolved = publicationReader.resolve(identity.sourceType(), identity.sourceRef());
    SourceDescriptor source = resolved.descriptor();
    requirePublishable(source);
    String sql = requireSql(resolved.sql());
    sqlCompiler.validateSelectOnly(sql);
    Optional<DataServiceDefinition> existing = dataServiceReader.findBySource(identity.sourceType(), identity.sourceRef());
    DataServiceSettings settings = normalizeSettings(provider.managesServiceDefinition()
        ? sourceManagedSettings(source, request, existing) : legacySettings(source, request, existing));
    DataServiceDefinition saved = manager.savePublished(existing.orElse(null), settings,
        new PublishedRuntimeSnapshot(source.dataSourceId(), sql),
        new SourceReference(identity.sourceType(), identity.sourceRef(), source.sourceRevisionId(), source.sourceRevisionNo()));
    if (provider.managesServiceDefinition()) syncSourceContract(saved.id(), resolved.contract());
    return viewFactory.view(saved);
  }

  @Transactional
  public DataServiceView updateSettings(Long apiId, PublicationSettings settings) {
    DataServiceDefinition current = dataServiceReader.require(apiId);
    if (isSourceManaged(current)) throw new IllegalStateException(
        "当前 API 定义由数据开发 Data Service Node 管理，请发布新的 Revision 后重新发布 Runtime");
    PublicationSettings values = settings == null
        ? new PublicationSettings(null, null, null, null, null, null)
        : settings;
    DataServiceSettings existing = current.settings();
    return viewFactory.view(manager.updateSettings(apiId, normalizeSettings(new DataServiceSettings(
        values.name(),
        values.path(),
        values.maxRows() == null ? existing.maxRows() : values.maxRows(),
        values.timeoutSeconds() == null ? existing.timeoutSeconds() : values.timeoutSeconds(),
        values.enabled() == null ? existing.enabled() : values.enabled(),
        values.description(),
        existing.paginationEnabled()))));
  }

  public boolean managesServiceDefinition(Long apiId) { return isSourceManaged(dataServiceReader.require(apiId)); }

  @Transactional
  public DataServiceView republish(Long apiId, PublicationSettings settings) {
    DataServiceDefinition current = dataServiceReader.require(apiId);
    SourceReference source = current.sourceReference();
    if (!StringUtils.hasText(source.sourceType()) || !StringUtils.hasText(source.sourceRef())) {
      throw new IllegalArgumentException("旧版手工数据服务没有发布来源，不能执行重新发布");
    }
    if (sourceRegistry.find(source.sourceType()) == null) throw new IllegalArgumentException(
        "该 API 使用历史冻结来源，Runtime Snapshot 可继续运行，但不再支持重新发布：" + source.sourceType());
    PublicationSettings values = settings == null ? new PublicationSettings(null, null, null, null, null, null) : settings;
    DataServiceView refreshed = publish(new PublishRequest(source.sourceType(), source.sourceRef(), values.name(), values.path(),
        values.maxRows(), values.timeoutSeconds(), values.enabled(), values.description()));
    if (!Objects.equals(apiId, refreshed.id())) throw new IllegalStateException("重新发布没有更新原数据服务，请检查来源唯一性：" + apiId);
    return refreshed;
  }

  private boolean isSourceManaged(DataServiceDefinition definition) {
    DataServiceSourceProvider provider = definition == null ? null : sourceRegistry.find(definition.sourceReference().sourceType());
    return provider != null && provider.managesServiceDefinition();
  }

  private DataServiceSettings sourceManagedSettings(SourceDescriptor source, PublishRequest request,
      Optional<DataServiceDefinition> existing) {
    return new DataServiceSettings(source.name(), source.defaultPath(), source.maxRows() == null ? DEFAULT_MAX_ROWS : source.maxRows(),
        source.timeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : source.timeoutSeconds(),
        request.enabled() != null ? request.enabled() : existing.map(v -> v.settings().enabled()).orElse(true),
        source.description(), Boolean.TRUE.equals(source.paginationEnabled()));
  }

  private DataServiceSettings legacySettings(SourceDescriptor source, PublishRequest request,
      Optional<DataServiceDefinition> existing) {
    DataServiceSettings current = existing.map(DataServiceDefinition::settings).orElse(null);
    return new DataServiceSettings(firstText(request.name(), current == null ? null : current.name(), source.name()),
        firstText(request.path(), current == null ? null : current.path(), source.defaultPath()),
        request.maxRows() != null ? request.maxRows() : current != null ? current.maxRows()
            : source.maxRows() == null ? DEFAULT_MAX_ROWS : source.maxRows(),
        request.timeoutSeconds() != null ? request.timeoutSeconds() : current != null ? current.timeoutSeconds()
            : source.timeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : source.timeoutSeconds(),
        request.enabled() != null ? request.enabled() : current != null ? current.enabled() : true,
        request.description() != null ? request.description() : current != null ? current.description() : source.description(),
        current != null ? current.paginationEnabled() : Boolean.TRUE.equals(source.paginationEnabled()));
  }

  private DataServiceSettings normalizeSettings(DataServiceSettings input) {
    if (input == null || !StringUtils.hasText(input.name())) throw new IllegalArgumentException("服务名称不能为空");
    String path = normalizePath(input.path());
    if (input.maxRows() < 1 || input.maxRows() > 10_000) throw new IllegalArgumentException("最大返回行数必须在 1~10000 之间");
    if (input.timeoutSeconds() < 1 || input.timeoutSeconds() > 3_600) throw new IllegalArgumentException("超时时间必须在 1~3600 秒之间");
    return new DataServiceSettings(input.name().trim(), path, input.maxRows(), input.timeoutSeconds(), input.enabled(),
        StringUtils.hasText(input.description()) ? input.description().trim() : null, input.paginationEnabled());
  }

  private void requirePublishable(SourceDescriptor source) {
    if (!"ONLINE".equalsIgnoreCase(source.status())) throw new IllegalArgumentException("只有 ONLINE 的发布来源可以发布/更新数据服务");
    if (source.sourceRevisionId() == null || source.sourceRevisionId() <= 0L || source.sourceRevisionNo() == null || source.sourceRevisionNo() <= 0)
      throw new IllegalArgumentException("发布来源缺少有效的不可变 Revision");
    if (source.dataSourceId() == null || source.dataSourceId() <= 0L) throw new IllegalArgumentException("发布来源缺少有效的数据源");
  }

  private String requireSql(String sql) { if (!StringUtils.hasText(sql)) throw new IllegalArgumentException("发布来源 SQL 不能为空"); return sql.trim(); }

  private void syncSourceContract(Long apiId, SourceContract contract) {
    SourceContract value = contract == null ? SourceContract.empty() : contract;
    List<ParameterDoc> parameters = value.parameters().stream()
        .map(item -> new ParameterDoc(item.name(), item.type(), item.required(), item.description(), item.example())).toList();
    List<ResponseFieldDoc> responseFields = value.responseFields().stream()
        .map(item -> new ResponseFieldDoc(item.name(), item.type(), item.nullable(), item.description(), item.example())).toList();
    documentationManager.save(apiId, new DocumentationInput(parameters, responseFields));
  }

  private String normalizePath(String path) {
    if (!StringUtils.hasText(path)) throw new IllegalArgumentException("服务路径不能为空");
    String value = path.trim(); if (!value.startsWith("/")) value = "/" + value; value = value.replaceAll("/{2,}", "/");
    if (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
    if (!value.matches("/[A-Za-z0-9._~/-]+")) throw new IllegalArgumentException("服务路径仅支持字母、数字、-、_、. 和 /：" + value);
    return value;
  }

  private String firstText(String... values) { for (String value : values) if (StringUtils.hasText(value)) return value.trim(); return null; }
}
