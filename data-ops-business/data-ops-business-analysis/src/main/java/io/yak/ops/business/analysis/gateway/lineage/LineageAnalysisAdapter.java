package io.yak.ops.business.analysis.gateway.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.maintenance.LineageMaintenanceService;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.lineage.registration.LineageRegistrationService;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Translates Analysis-owned lineage specs to the shared Lineage module API. */
@Component
public class LineageAnalysisAdapter implements AnalysisLineageGraphGateway {

  private final LineageQueryService queryService;
  private final LineageRegistrationService writeService;
  private final LineageMaintenanceService maintenance;
  private final ObjectMapper objectMapper;

  public LineageAnalysisAdapter(
      LineageQueryService queryService,
      LineageRegistrationService writeService,
      LineageMaintenanceService maintenance,
      ObjectMapper objectMapper) {
    this.queryService = queryService;
    this.writeService = writeService;
    this.maintenance = maintenance;
    this.objectMapper = objectMapper;
  }

  @Override
  public void clearRelationsByEvidence(String evidenceSourceType, String evidenceId) {
    maintenance.clearRelationsByEvidence(evidenceSourceType, evidenceId);
  }

  @Override
  public Asset registerAsset(AssetSpec spec) {
    LineageAsset asset = writeService.registerAsset(new LineageRegistrationService.RegisterAssetCommand(
        spec.assetKey(),
        assetType(spec.assetType()),
        spec.name(),
        spec.sourceType(),
        spec.sourceId(),
        spec.parentAssetId(),
        spec.dataSourceId(),
        spec.databaseName(),
        spec.schemaName(),
        spec.tableName(),
        spec.columnName(),
        properties(spec.properties())));
    return new Asset(asset.id(), asset.assetKey());
  }

  @Override
  public Asset requireAssetByKey(String assetKey) {
    LineageAsset asset = queryService.getAssetByKey(assetKey);
    return new Asset(asset.id(), asset.assetKey());
  }

  @Override
  public void registerRelation(RelationSpec spec) {
    writeService.registerRelation(new LineageRegistrationService.RegisterRelationCommand(
        spec.sourceAssetId(),
        spec.targetAssetId(),
        relationType(spec.relationType()),
        spec.sourceType(),
        spec.sourceId(),
        spec.expression(),
        spec.confidence(),
        spec.version(),
        spec.observedAt(),
        properties(spec.properties())));
  }

  private LineageAssetType assetType(AssetType type) {
    return switch (type) {
      case CHART -> LineageAssetType.CHART;
      case DATASET -> LineageAssetType.DATASET;
      case DATASET_FIELD -> LineageAssetType.DATASET_FIELD;
    };
  }

  private LineageRelationType relationType(RelationType type) {
    return switch (type) {
      case CONSUMES -> LineageRelationType.CONSUMES;
    };
  }

  private JsonNode properties(Map<String, Object> values) {
    return values == null ? null : objectMapper.valueToTree(values);
  }
}
