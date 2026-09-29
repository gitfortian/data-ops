package io.yak.ops.business.dataservice.publication;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.SourceDescriptor;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.SourcePage;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServicePublicationReader {

  private static final int MAX_SOURCE_PAGE_SIZE = 100;
  private final DataServiceSourceRegistry sourceRegistry;
  private final DataServiceReader dataServiceReader;
  private final DataServiceViewFactory viewFactory;

  public boolean managesServiceDefinition(String sourceType) {
    return sourceRegistry.require(sourceType).managesServiceDefinition();
  }

  public SourcePage sources(String sourceType, int pageNo, int pageSize, String keyword) {
    return sourceRegistry.require(sourceType).list(
        Math.max(1, pageNo), Math.max(1, Math.min(MAX_SOURCE_PAGE_SIZE, pageSize)), normalizeKeyword(keyword));
  }

  public PublicationState state(String sourceType, String sourceRef) {
    SourceIdentity identity = normalizeIdentity(sourceType, sourceRef);
    ResolvedSource resolved = requireResolvedSource(sourceRegistry.require(identity.sourceType()), identity.sourceRef());
    SourceDescriptor source = resolved.descriptor();
    Optional<DataServiceDefinition> existing =
        dataServiceReader.findBySource(identity.sourceType(), identity.sourceRef());
    boolean updateAvailable = existing
        .map(api -> !Objects.equals(api.sourceReference().sourceRevisionId(), source.sourceRevisionId()))
        .orElse(false);
    return new PublicationState(
        existing.isPresent(), updateAvailable, source, existing.map(viewFactory::view).orElse(null));
  }

  ResolvedSource resolve(String sourceType, String sourceRef) {
    SourceIdentity identity = normalizeIdentity(sourceType, sourceRef);
    return requireResolvedSource(sourceRegistry.require(identity.sourceType()), identity.sourceRef());
  }

  SourceIdentity normalizeIdentity(String sourceType, String sourceRef) {
    String type = sourceRegistry.normalizeSourceType(sourceType);
    if (sourceRef == null || sourceRef.isBlank()) throw new IllegalArgumentException("sourceRef 不能为空");
    return new SourceIdentity(type, sourceRef.trim());
  }

  private ResolvedSource requireResolvedSource(DataServiceSourceProvider provider, String sourceRef) {
    ResolvedSource resolved = provider.resolve(sourceRef);
    if (resolved == null || resolved.descriptor() == null) {
      throw new IllegalStateException("发布来源解析结果为空：" + provider.sourceType() + "/" + sourceRef);
    }
    SourceDescriptor source = resolved.descriptor();
    SourceIdentity descriptor = normalizeIdentity(source.sourceType(), source.sourceRef());
    SourceIdentity requested = normalizeIdentity(provider.sourceType(), sourceRef);
    if (!descriptor.equals(requested)) {
      throw new IllegalStateException("发布来源返回了不匹配的来源标识：" + source.sourceType() + "/" + source.sourceRef());
    }
    return resolved;
  }

  private String normalizeKeyword(String keyword) {
    return keyword == null || keyword.isBlank() ? null : keyword.trim();
  }

  record SourceIdentity(String sourceType, String sourceRef) {}
}
