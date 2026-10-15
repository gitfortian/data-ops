package io.yak.ops.business.modeling.deployment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.modeling.dao.mapper.OdsTargetObservationMapper;
import io.yak.ops.business.modeling.dao.model.OdsTargetObservationPO;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.version.ModelVersionService;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only observations of exact ODS target catalog state.
 * Never assert deployment execution / synchronization / data quality.
 */
@Service
@RequiredArgsConstructor
public class OdsTargetObservationService {
  private static final Duration EVIDENCE_MAX_AGE = Duration.ofMinutes(15);

  private final CurrentProject currentProject;
  private final ActionAuthorization authorization;
  private final ModelRepository models;
  private final ModelVersionService versions;
  private final OdsPhysicalTargetInspectionService inspector;
  private final OdsTargetObservationMapper mapper;

  public record View(Long id, Long modelId, int versionNo, Long modelVersionId,
                     Long targetDatasourceId, String targetDatabase, String targetSchema,
                     String targetTable, String observedCatalogSha256,
                     String frozenVersionChecksum, int observedColumnCount,
                     String observedBy, LocalDateTime observedAt,
                     String status, List<String> limitations) {}

  /**
   * Commit an observation only after fresh reinspection in the same operation.
   * A client-supplied hash, cached UI report or model/version hint is never accepted as proof.
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public View observe(Long modelId, int versionNo, Long targetDatasourceId,
                      String database, String schema, String operator) {
    requireReads();
    if (operator == null || operator.isBlank()) throw invalid("缺少可信操作员身份");
    Long projectId = currentProject.requireProjectId();
    // Serializes concurrent version-pointer changes and the observation for the same model.
    Model locked = models.findByIdForUpdate(modelId)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, "模型不存在或无权访问"));
    if (locked.isRecycled() || locked.publishedVersionId() == null
        || !Objects.equals(locked.latestVersionNo(), versionNo)) {
      throw invalid("目标核验期间模型版本已变化；请重新核验当前发布版本");
    }
    var report = inspector.inspect(modelId, versionNo, targetDatasourceId, database, schema);
    if (!"COLUMN_MATCH_NEEDS_DDL_REVIEW".equals(report.state())
        || report.observedCatalogSha256() == null
        || report.observedCatalogSha256().length() != 64) {
      throw invalid("目标结构当前不完全可核验；不能记录匹配证据");
    }
    var version = versions.getVersionDetail(modelId, versionNo);
    if (version == null || version.version() == null
        || !Objects.equals(report.versionId(), version.version().id())
        || !Objects.equals(locked.publishedVersionId(), version.version().id())
        || version.version().checksum() == null) {
      throw invalid("发布版本指针与当前目标核验不一致");
    }
    var po = new OdsTargetObservationPO();
    po.setProjectId(projectId);
    po.setModelId(modelId);
    po.setModelVersionId(version.version().id());
    po.setVersionNo(versionNo);
    po.setTargetDatasourceId(targetDatasourceId);
    po.setTargetDatabase(report.targetDatabase());
    po.setTargetSchema(report.targetSchema());
    po.setTargetTable(report.targetTable());
    po.setCatalogSha256(report.observedCatalogSha256());
    po.setFrozenChecksum(version.version().checksum());
    po.setObservedColumnCount(report.observedColumns());
    po.setObservedBy(operator);
    po.setObservedAt(LocalDateTime.now());
    mapper.insert(po);
    if (po.getId() == null) throw new IllegalStateException("目标核验记录未生成 ID");
    return toView(po, locked);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public List<View> history(Long modelId) {
    requireReads();
    if (modelId == null || modelId <= 0) throw invalid("模型 ID 无效");
    Long projectId = currentProject.requireProjectId();
    Model model = models.findById(modelId).orElseThrow(() ->
        new ModelingException(ModelingErrorCode.NOT_FOUND, "模型不存在或无权访问"));
    if (model.isRecycled() || !"ODS".equalsIgnoreCase(model.layerCode())) {
      throw invalid("只支持当前项目有效 ODS");
    }
    return mapper.selectList(new LambdaQueryWrapper<OdsTargetObservationPO>()
        .eq(OdsTargetObservationPO::getProjectId, projectId)
        .eq(OdsTargetObservationPO::getModelId, modelId)
        .orderByDesc(OdsTargetObservationPO::getId)
        .last("LIMIT 20")).stream().map(po -> toView(po, model)).toList();
  }

  private View toView(OdsTargetObservationPO po, Model model) {
    boolean currentVersion = Objects.equals(model.publishedVersionId(), po.getModelVersionId())
        && Objects.equals(model.latestVersionNo(), po.getVersionNo());
    boolean recent = po.getObservedAt() != null
        && !po.getObservedAt().isAfter(LocalDateTime.now())
        && Duration.between(po.getObservedAt(), LocalDateTime.now()).compareTo(EVIDENCE_MAX_AGE) <= 0;
    String state = !currentVersion ? "VERSION_CHANGED"
        : recent ? "RECENT_CATALOG_OBSERVATION" : "STALE_CATALOG_OBSERVATION";
    return new View(po.getId(), po.getModelId(), po.getVersionNo(), po.getModelVersionId(),
        po.getTargetDatasourceId(), po.getTargetDatabase(), po.getTargetSchema(),
        po.getTargetTable(), po.getCatalogSha256(), po.getFrozenChecksum(),
        po.getObservedColumnCount(), po.getObservedBy(), po.getObservedAt(), state,
        List.of("该记录仅证明指定时刻 Catalog 的物理列观察，不证明谁执行了 DDL、是否同步数据或索引/分区完全一致",
            "即使记录状态 RECENT，也必须在同步执行前独立重新核验来源、目标环境、目标任务版本和权限",
            "记录会随时间或模型发布版本变化而过期，不能作为永久部署许可"));
  }

  private void requireReads() {
    authorization.requirePermission(ModelingPermissionCode.READ);
    authorization.requirePermission(DataSourcePermissionCode.READ);
  }

  private static ModelingException invalid(String message) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, message);
  }
}
