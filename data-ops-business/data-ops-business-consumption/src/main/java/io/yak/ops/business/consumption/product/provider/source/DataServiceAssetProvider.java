package io.yak.ops.business.consumption.product.provider.source;

import io.yak.ops.business.asset.api.AssetContentHash;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceIdentity;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import io.yak.ops.core.project.CurrentProject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Read-only Asset projection; source definition and key remain owned by Data Service. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceAssetProvider implements AssetProvider {
  private final DataServiceReader reader;
  private final CurrentProject currentProject;

  @Override
  public AssetSourceType sourceType() {
    return AssetSourceType.DATA_SERVICE;
  }

  @Override
  public AssetPage cursorList(AssetCursorQuery query) {
    if (!currentProject.requireProjectId().equals(query.projectId())) {
      throw new IllegalArgumentException("Asset reconciliation project does not match the source context");
    }
    Long cursor = query.cursor() == null ? null : positiveId(query.cursor());
    List<DataServiceDefinition> sources = reader.cursorList(cursor, query.updatedAfter(), query.limit());
    String next = sources.size() == query.limit() ? String.valueOf(sources.getLast().id()) : null;
    return new AssetPage(sources.stream().map(this::descriptor).toList(), next);
  }

  @Override
  public Optional<AssetDescriptor> refresh(String sourceId) {
    return reader.find(positiveId(sourceId)).map(this::descriptor);
  }

  private AssetDescriptor descriptor(DataServiceDefinition source) {
    var settings = source.settings();
    var revision = source.sourceReference();
    Map<String, String> extra = new LinkedHashMap<>();
    extra.put("enabled", String.valueOf(settings.enabled()));
    extra.put("path", settings.path());
    extra.put("runtimeGeneration", String.valueOf(source.runtimeGeneration()));
    if (revision.sourceRevisionId() != null) {
      extra.put("sourceRevisionId", String.valueOf(revision.sourceRevisionId()));
    }
    return new AssetDescriptor(
        DataServiceIdentity.assetKey(source.id()), String.valueOf(source.id()),
        settings.name(), settings.description(), AssetType.DATA_SERVICE, null, null, null,
        source.updateTime(), AssetContentHash.of(settings.name(), settings.description(), settings.path(),
            String.valueOf(settings.enabled()), String.valueOf(revision.sourceRevisionId())), extra);
  }

  private static Long positiveId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id > 0L) return id;
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("Invalid Data Service identity");
    }
    throw new IllegalArgumentException("Invalid Data Service identity");
  }
}
