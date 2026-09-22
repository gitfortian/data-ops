package io.yak.ops.business.dataset.gateway.lineage;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.maintenance.LineageMaintenanceService;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.lineage.registration.LineageRegistrationService;
import io.yak.ops.core.project.CurrentProject;
import org.springframework.stereotype.Component;

/** Adapts the shared lineage graph API to Dataset-owned asset/relation values. */
@Component
public class LineageGraphDatasetAdapter implements DatasetLineageGraphGateway {

  private final LineageQueryService queryService;
  private final LineageRegistrationService writeService;
  private final LineageMaintenanceService maintenanceService;
  private final CurrentProject currentProject;

  public LineageGraphDatasetAdapter(
      LineageQueryService queryService,
      LineageRegistrationService writeService,
      LineageMaintenanceService maintenanceService,
      CurrentProject currentProject) {
    this.queryService = queryService;
    this.writeService = writeService;
    this.maintenanceService = maintenanceService;
    this.currentProject = currentProject;
  }

  @Override
  public void clearRelationsByEvidence(String evidenceSourceType, String evidenceId) {
    currentProject.requireProjectId();
    maintenanceService.clearRelationsByEvidence(evidenceSourceType, evidenceId);
  }

  @Override
  public Asset registerAsset(AssetSpec spec) {
    Long sourceProjectId = currentProject.requireProjectId();
    LineageAsset asset =
        writeService.registerAsset(
            new LineageRegistrationService.RegisterAssetCommand(
                spec.assetKey(),
                LineageAssetType.valueOf(spec.assetType().name()),
                spec.name(),
                spec.sourceType(),
                spec.sourceId(),
                spec.parentAssetId(),
                spec.dataSourceId(),
                spec.databaseName(),
                spec.schemaName(),
                spec.tableName(),
                spec.columnName(),
                spec.properties(),
                sourceProjectId));
    return new Asset(asset.id(), asset.assetKey());
  }

  @Override
  public Asset requireAssetByKey(String assetKey) {
    currentProject.requireProjectId();
    LineageAsset asset = queryService.getAssetByKey(assetKey);
    return new Asset(asset.id(), asset.assetKey());
  }

  @Override
  public void registerRelation(RelationSpec spec) {
    Long sourceProjectId = currentProject.requireProjectId();
    writeService.registerRelation(
        new LineageRegistrationService.RegisterRelationCommand(
            spec.sourceAssetId(),
            spec.targetAssetId(),
            LineageRelationType.valueOf(spec.relationType().name()),
            spec.sourceType(),
            spec.sourceId(),
            spec.expression(),
            spec.confidence(),
            spec.version(),
            spec.observedAt(),
            spec.properties(),
            sourceProjectId));
  }
}
