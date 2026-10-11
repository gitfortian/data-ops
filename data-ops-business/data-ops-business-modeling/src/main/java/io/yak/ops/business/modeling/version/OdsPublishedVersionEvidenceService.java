package io.yak.ops.business.modeling.version;

import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.mapping.OdsSourceEvidenceInspectionService;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only comparison of one exact historical ODS structure version with the
 * current structure and current authorized source evidence. This is NOT a
 * historical provenance manifest and is never usable as a publication/run gate.
 */
@Service
@RequiredArgsConstructor
public class OdsPublishedVersionEvidenceService {
  private static final int MAX_COLUMNS = 1000;

  private final ModelRepository models;
  private final ModelVersionService versions;
  private final ModelStructureService structures;
  private final OdsSourceEvidenceInspectionService currentSource;

  public record ColumnDifference(String column, String status, String frozenType,
                                 String currentType) {}
  public record Report(Long modelId, int versionNo, Long versionId, String modelStatus,
                       boolean latestPublishedVersion, String structureStatus,
                       int frozenColumnCount, int currentColumnCount,
                       List<ColumnDifference> differences, String currentSourceStatus,
                       String currentSourceEvidenceSha256, String historicalSourceStatus,
                       List<String> limitations) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Report review(Long modelId, int versionNo) {
    if (modelId == null || modelId <= 0 || versionNo <= 0) {
      throw invalid("须指定当前项目内的有效模型与精确发布版本");
    }
    Model model = models.findById(modelId).orElseThrow(() ->
        new ModelingException(ModelingErrorCode.NOT_FOUND, "模型不存在或不属于当前项目"));
    if (model.isRecycled() || !"ODS".equalsIgnoreCase(model.layerCode())) {
      throw invalid("仅核验未回收 ODS 的指定物理设计版本");
    }

    // ModelVersionService checks project-scoped model ownership before reading
    // the exact immutable version. Reject mismatched legacy/corrupt identities.
    ModelVersionService.VersionDetailView version = versions.getVersionDetail(modelId, versionNo);
    if (version == null || version.version() == null || version.structure() == null
        || !Objects.equals(version.version().modelId(), modelId)
        || version.version().versionNo() != versionNo
        || !Objects.equals(version.structure().modelId(), modelId)
        || !"ODS".equalsIgnoreCase(String.valueOf(version.meta().get("layerCode")))) {
      throw invalid("版本结构、发布时分层或模型身份不匹配，无法形成可信证据");
    }

    StructureView frozen = version.structure();
    StructureView live = structures.get(modelId);
    if (live == null || !Objects.equals(live.modelId(), modelId)) {
      throw invalid("当前物理结构身份不一致，禁止跨模型比较");
    }
    List<StructureView.ColumnView> historicalColumns = frozen.columns() == null
        ? List.of() : frozen.columns();
    List<StructureView.ColumnView> liveColumns = live.columns() == null
        ? List.of() : live.columns();
    if (historicalColumns.size() > MAX_COLUMNS || liveColumns.size() > MAX_COLUMNS) {
      throw invalid("模型字段过多，不能完整比较当前版本");
    }
    Map<String, StructureView.ColumnView> liveByName = new HashMap<>();
    Set<String> historicalNames = new HashSet<>();
    for (StructureView.ColumnView column : liveColumns) {
      if (column == null || column.columnName() == null
          || liveByName.putIfAbsent(key(column.columnName()), column) != null) {
        throw invalid("当前物理列身份无效或重复，禁止对比");
      }
    }
    List<ColumnDifference> differences = new ArrayList<>();
    for (StructureView.ColumnView frozenColumn : historicalColumns) {
      if (frozenColumn == null || frozenColumn.columnName() == null
          || !historicalNames.add(key(frozenColumn.columnName()))) {
        throw invalid("冻结版本含缺失或重复的物理列身份");
      }
      StructureView.ColumnView present = liveByName.get(key(frozenColumn.columnName()));
      if (present == null) {
        differences.add(new ColumnDifference(frozenColumn.columnName(), "REMOVED",
            frozenColumn.dataType(), null));
      } else if (!sameColumn(frozenColumn, present)) {
        differences.add(new ColumnDifference(frozenColumn.columnName(), "CHANGED",
            frozenColumn.dataType(), present.dataType()));
      }
    }
    for (StructureView.ColumnView present : liveColumns) {
      if (!historicalNames.contains(key(present.columnName()))) {
        differences.add(new ColumnDifference(present.columnName(), "ADDED",
            null, present.dataType()));
      }
    }

    // Compare full semantic structure, including keys/indexes/partition/standards,
    // not just column names. Never treat a changed PK as an identical version.
    boolean sameStructure = ModelVersionService.semanticView(frozen)
        .equals(ModelVersionService.semanticView(live));
    if (!sameStructure && differences.isEmpty()) {
      differences.add(new ColumnDifference("表级定义（表名、主键、索引、分区或属性）",
          "TABLE_DEFINITION_CHANGED", null, null));
    }

    // Today's source read is explicitly CURRENT; MappingRepository wasn't
    // snapshotted into the historical version by the existing publish contract.
    // Catalog failures propagate (UNKNOWN is never converted to SUCCESS).
    var source = currentSource.inspect(modelId);
    List<String> limitations = new ArrayList<>();
    limitations.add("该版本冻结的是物理表结构；当时的 SourceRef 与逐列 Mapping 未随版本冻结");
    limitations.add("当前来源报告是本次实时读取，不代表版本发布当时的来源映射或数据快照");
    limitations.add("核对通过不代表已建表、已同步、已运行、数据质量或受治理消费通过");
    if (!sameStructure) limitations.add("当前草稿结构与此发布版本不同，须明确比较版本");
    if (!"CONSISTENT_NOW".equals(source.status())) {
      limitations.add("当前来源存在待复核或不可核验项，不得当作历史版本可信来源");
    }
    return new Report(modelId, versionNo, version.version().id(),
        model.status().name(), Objects.equals(model.latestVersionNo(), versionNo),
        sameStructure ? "MATCHES_CURRENT" : "DIFFERS_FROM_CURRENT",
        historicalColumns.size(), liveColumns.size(), List.copyOf(differences),
        source.status(), source.evidenceSha256(), "NOT_FROZEN",
        List.copyOf(limitations));
  }

  private static boolean sameColumn(StructureView.ColumnView a, StructureView.ColumnView b) {
    // Row identity is volatile on structure full-replace; compare business semantics.
    return Objects.equals(a.columnName(), b.columnName())
        && Objects.equals(a.dataType(), b.dataType())
        && Objects.equals(a.length(), b.length())
        && Objects.equals(a.scale(), b.scale())
        && Objects.equals(a.nullable(), b.nullable())
        && Objects.equals(a.defaultValue(), b.defaultValue())
        && Objects.equals(a.comment(), b.comment())
        && Objects.equals(a.businessDescription(), b.businessDescription())
        && Objects.equals(a.sortOrder(), b.sortOrder())
        && Objects.equals(a.stdTypeId(), b.stdTypeId())
        && Objects.equals(a.stdNamingId(), b.stdNamingId())
        && Objects.equals(a.stdCodeSetCode(), b.stdCodeSetCode())
        && Objects.equals(a.stdUnitId(), b.stdUnitId())
        && Objects.equals(a.stdCaliberId(), b.stdCaliberId())
        && Objects.equals(a.stdSecurityId(), b.stdSecurityId())
        && Objects.equals(a.stdFieldId(), b.stdFieldId())
        && Objects.equals(a.fieldRole(), b.fieldRole())
        && Objects.equals(a.aggregateFunc(), b.aggregateFunc())
        && Objects.equals(a.transformExpr(), b.transformExpr());
  }

  private static String key(String value) { return value.trim().toLowerCase(Locale.ROOT); }
  private static ModelingException invalid(String explanation) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, explanation);
  }
}
