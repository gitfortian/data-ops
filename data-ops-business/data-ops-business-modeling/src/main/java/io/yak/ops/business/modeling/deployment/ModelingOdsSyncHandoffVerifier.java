package io.yak.ops.business.modeling.deployment;

import io.yak.ops.business.modeling.dao.mapper.OdsTargetObservationMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.mapping.OdsSourceEvidenceInspectionService;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.modeling.OdsSyncHandoffVerifier;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Modeling owns the ODS/target evidence. Offline cannot declare it valid by
 * serializing a UI route, self-supplied ID, or stale target observation.
 */
@Service
@RequiredArgsConstructor
public class ModelingOdsSyncHandoffVerifier implements OdsSyncHandoffVerifier {
  private static final Duration MAX_AGE = Duration.ofMinutes(15);
  private final CurrentProject currentProject;
  private final ActionAuthorization authorization;
  private final ModelRepository models;
  private final OdsTargetObservationMapper observations;
  private final OdsPhysicalTargetInspectionService inspector;
  private final OdsSourceEvidenceInspectionService sourceEvidence;

  @Override
  public void verify(long projectId, long modelId, int versionNo, long observationId,
                     long sourceDatasourceId, String sourceTable,
                     long sinkDatasourceId, String sinkTable) {
    authorization.requirePermission(ModelingPermissionCode.READ);
    authorization.requirePermission(DataSourcePermissionCode.READ);
    if (projectId <= 0 || projectId != currentProject.requireProjectId()
        || modelId <= 0 || versionNo <= 0 || observationId <= 0
        || sourceDatasourceId <= 0 || sinkDatasourceId <= 0) {
      throw new IllegalArgumentException("ODS 同步交接不属于当前项目或缺少有效身份");
    }
    Model model = models.findById(modelId)
        .orElseThrow(() -> new IllegalArgumentException("当前项目内没有可读 ODS 模型"));
    if (model.isRecycled() || !"ODS".equalsIgnoreCase(model.layerCode())
        || model.publishedVersionId() == null
        || !Objects.equals(model.latestVersionNo(), versionNo)
        || !Objects.equals(model.sourceDatasourceId(), sourceDatasourceId)
        || !matchesTable(sourceTable, model.sourceDatabase(), model.sourceSchema(), model.sourceTable())) {
      throw new IllegalArgumentException("离线任务的来源与当前 ODS 已发布版本不一致");
    }

    var observation = observations.selectById(observationId);
    if (observation == null || !Objects.equals(observation.getProjectId(), projectId)
        || !Objects.equals(observation.getModelId(), modelId)
        || !Objects.equals(observation.getModelVersionId(), model.publishedVersionId())
        || !Objects.equals(observation.getVersionNo(), versionNo)
        || !Objects.equals(observation.getTargetDatasourceId(), sinkDatasourceId)
        || !matchesTable(sinkTable, observation.getTargetDatabase(),
            observation.getTargetSchema(), observation.getTargetTable())) {
      throw new IllegalArgumentException("离线任务目标和 ODS 目标观察证据不匹配");
    }
    LocalDateTime now = LocalDateTime.now();
    if (observation.getObservedAt() == null
        || observation.getObservedAt().isAfter(now)
        || Duration.between(observation.getObservedAt(), now).compareTo(MAX_AGE) > 0) {
      throw new IllegalArgumentException("ODS 目标观察已过期，必须重新核验目标 Catalog");
    }

    var currentSource = sourceEvidence.inspect(modelId);
    if (!"CONSISTENT_NOW".equals(currentSource.status())
        || currentSource.evidenceSha256() == null) {
      throw new IllegalArgumentException("ODS 当前真实来源或逐列映射已经漂移");
    }
    var live = inspector.inspect(modelId, versionNo, sinkDatasourceId,
        observation.getTargetDatabase(), observation.getTargetSchema());
    if (!"COLUMN_MATCH_NEEDS_DDL_REVIEW".equals(live.state())
        || !Objects.equals(live.versionId(), observation.getModelVersionId())
        || !Objects.equals(live.targetTable(), observation.getTargetTable())
        || live.observedCatalogSha256() == null
        || !Objects.equals(live.observedCatalogSha256(), observation.getCatalogSha256())) {
      throw new IllegalArgumentException("目标物理表 Catalog 与保存的 ODS 证据不一致，禁止绑定任务");
    }
  }

  private static boolean matchesTable(String submitted, String database, String schema,
                                      String table) {
    if (submitted == null || table == null) return false;
    // Never infer a named schema from an unqualified table (e.g. public vs archive).
    if (schema != null && !schema.isBlank()) {
      return submitted.equals(schema + "." + table)
          || (database != null && submitted.equals(database + "." + schema + "." + table));
    }
    return submitted.equals(table)
        || (database != null && submitted.equals(database + "." + table));
  }
}
