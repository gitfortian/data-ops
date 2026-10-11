package io.yak.ops.business.modeling.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureRepository;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Repair unmapped ODS source columns only. Preview is evidence, not a write;
 * confirmation is all-or-nothing against freshly read source and draft evidence.
 * Existing mappings, structures and published models are never overwritten.
 */
@Service
@RequiredArgsConstructor
public class OdsSourceMappingReconcileService {
  private static final int MAX_COLUMNS = 1000;
  private final ModelRepository models;
  private final ModelStructureRepository structures;
  private final MappingRepository mappings;
  private final DataSourceCatalogReader sourceCatalog;
  private final ActionAuthorization authorization;
  private final ObjectMapper json;

  public record MappingRow(String targetColumn, String dataType, String status,
                           String existingSourceColumn, String suggestedSourceColumn) {}
  public record Preview(Long modelId, Long sourceDatasourceId, String sourceDatabase,
                        String sourceTable, String definition, List<String> sourceColumns,
                        List<MappingRow> rows, int mappedCount, int unmappedCount,
                        int technicalCount, int orphanCount) {}
  public record Selection(String targetColumn, String sourceColumn) {}
  public record ConfirmRequest(String expectedDefinition, List<Selection> selections) {}
  public record Receipt(Long modelId, int applied, int mappedCount,
                        List<String> confirmedTargets, String status) {}
  private record Evidence(Model model, List<ColumnDefinition> columns,
                          List<ModelingColumnMappingPO> mappings, List<CatalogColumn> sources) {}
  private record State(Preview preview, Model model, Map<String, ColumnDefinition> columns,
                       Map<String, ModelingColumnMappingPO> existing, Map<String, CatalogColumn> sources) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Preview preview(Long modelId) {
    authorization.requirePermission(ModelingPermissionCode.READ);
    authorization.requirePermission(DataSourcePermissionCode.READ);
    return read(modelId, false).preview();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Receipt confirm(Long modelId, ConfirmRequest request, String operator) {
    authorization.requirePermission(ModelingPermissionCode.UPDATE);
    authorization.requirePermission(DataSourcePermissionCode.READ);
    State state = read(modelId, true);
    if (request == null || request.expectedDefinition() == null
        || !request.expectedDefinition().matches("[0-9a-f]{64}")
        || !Objects.equals(request.expectedDefinition(), state.preview().definition())) {
      throw reject("来源、目标结构或映射已变化，请重新预检并确认");
    }
    List<Selection> selected = request.selections();
    if (selected == null || selected.isEmpty() || selected.size() > MAX_COLUMNS) {
      throw reject("请选择 1～" + MAX_COLUMNS + " 个未映射的目标字段");
    }
    Set<String> usedTargets = new HashSet<>();
    Set<String> usedSources = new HashSet<>();
    for (ModelingColumnMappingPO prior : state.existing().values()) {
      if (prior.getSourceColumn() != null && sameBinding(state.model(), prior)) {
        usedSources.add(key(prior.getSourceColumn()));
      }
    }
    List<ModelingColumnMappingPO> pending = new ArrayList<>();
    for (Selection pair : selected) {
      if (pair == null || !StringUtils.hasText(pair.targetColumn())
          || !StringUtils.hasText(pair.sourceColumn())) {
        throw reject("必须逐项指定目标字段和真实源字段");
      }
      String targetKey = key(pair.targetColumn());
      String sourceKey = key(pair.sourceColumn());
      ColumnDefinition target = state.columns().get(targetKey);
      CatalogColumn source = state.sources().get(sourceKey);
      if (target == null || source == null) throw reject("选中字段不属于本次已授权来源与物理结构");
      if (!usedTargets.add(targetKey) || !usedSources.add(sourceKey)) {
        throw reject("ODS 来源保真修复要求一对一字段引用，重复映射被拒绝");
      }
      if (state.existing().containsKey(targetKey)) {
        throw reject("该目标字段已有人工来源映射，不允许批量覆盖");
      }
      if (technical(target.columnName(), state.sources())) {
        throw reject("平台技术字段无真实同名来源，不能伪装成源字段映射");
      }
      ModelingColumnMappingPO mapping = new ModelingColumnMappingPO();
      mapping.setModelId(modelId);
      mapping.setTargetColumn(target.columnName());
      mapping.setSourceDatasourceId(state.model().sourceDatasourceId());
      mapping.setSourceDatabase(state.model().sourceDatabase());
      mapping.setSourceTable(state.model().sourceTable());
      mapping.setSourceColumn(source.name());
      mapping.setTransformExpr(null);
      pending.add(mapping);
    }
    // All validation precedes the first write; one Modeling transaction owns all writes.
    for (ModelingColumnMappingPO mapping : pending) mappings.upsert(mapping, operator);
    // Do not claim success based on the write call alone; read back the persisted source refs.
    List<ModelingColumnMappingPO> persisted = mappings.listByModel(modelId);
    for (ModelingColumnMappingPO expected : pending) {
      boolean found = persisted.stream().anyMatch(actual ->
          key(actual.getTargetColumn()).equals(key(expected.getTargetColumn()))
              && sameBinding(state.model(), actual)
              && Objects.equals(actual.getSourceColumn(), expected.getSourceColumn())
              && actual.getTransformExpr() == null);
      if (!found) throw reject("来源映射落库回读不一致，本次修复事务回滚");
    }
    return new Receipt(modelId, pending.size(), persisted.size(),
        pending.stream().map(ModelingColumnMappingPO::getTargetColumn).toList(), "SAVED_AND_VERIFIED");
  }

  private State read(Long modelId, boolean forUpdate) {
    if (modelId == null || modelId <= 0) throw reject("请选择项目内 ODS 模型");
    Model model = (forUpdate ? models.findByIdForUpdate(modelId) : models.findById(modelId))
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, "模型不存在或无权访问"));
    if (!"ODS".equalsIgnoreCase(model.layerCode())
        || model.status() != ModelStatus.DRAFT || model.isRecycled()) {
      throw reject("仅允许修复未发布且未回收的 ODS 草稿，禁止改写已发布版本");
    }
    if (model.sourceDatasourceId() == null || !StringUtils.hasText(model.sourceTable())) {
      throw reject("模型没有正式来源绑定，先使用原有授权导入流程，不按字段名猜测来源");
    }
    List<ColumnDefinition> columns = forUpdate
        ? structures.findColumnsForUpdate(modelId) : structures.findColumns(modelId);
    List<ModelingColumnMappingPO> existing = mappings.listByModel(modelId);
    List<CatalogColumn> source = sourceCatalog.listColumnsFresh(
        model.sourceDatasourceId(), model.sourceDatabase(), null, model.sourceTable());
    if (columns == null || source == null || columns.isEmpty() || source.isEmpty()
        || columns.size() > MAX_COLUMNS || source.size() > MAX_COLUMNS
        || existing.size() > MAX_COLUMNS) {
      throw reject("来源或目标列为空、过多或当前不可验证，禁止批量修复");
    }
    Map<String, ColumnDefinition> targets = new HashMap<>();
    Map<String, CatalogColumn> sources = new HashMap<>();
    Map<String, ModelingColumnMappingPO> saved = new HashMap<>();
    for (ColumnDefinition column : columns) {
      if (column.columnName() == null
          || targets.putIfAbsent(key(column.columnName()), column) != null) {
        throw reject("目标列身份缺失或重复，不能进行准确映射");
      }
    }
    for (CatalogColumn column : source) {
      if (column.name() == null || sources.putIfAbsent(key(column.name()), column) != null) {
        throw reject("来源列身份缺失或重复，不能进行准确映射");
      }
    }
    for (ModelingColumnMappingPO mapping : existing) {
      if (mapping.getTargetColumn() == null
          || saved.putIfAbsent(key(mapping.getTargetColumn()), mapping) != null) {
        throw reject("已有来源映射身份重复，必须先人工清理");
      }
    }
    List<MappingRow> rows = new ArrayList<>();
    int mapped = 0;
    int unmapped = 0;
    int generated = 0;
    for (ColumnDefinition column : columns) {
      String name = column.columnName();
      ModelingColumnMappingPO existingMapping = saved.get(key(name));
      if (existingMapping != null) {
        mapped++;
        rows.add(new MappingRow(name, column.dataType(), "MAPPED",
            existingMapping.getSourceColumn(), null));
      } else if (technical(name, sources)) {
        generated++;
        rows.add(new MappingRow(name, column.dataType(), "TECHNICAL", null, null));
      } else {
        unmapped++;
        CatalogColumn candidate = sources.get(key(name));
        rows.add(new MappingRow(name, column.dataType(), "UNMAPPED", null,
            candidate == null ? null : candidate.name()));
      }
    }
    int orphan = (int) saved.keySet().stream().filter(key -> !targets.containsKey(key)).count();
    List<ColumnDefinition> orderedColumns = columns.stream()
        .sorted(Comparator.comparing(c -> key(c.columnName()))).toList();
    List<ModelingColumnMappingPO> orderedMappings = existing.stream()
        .sorted(Comparator.comparing(m -> key(m.getTargetColumn()))).toList();
    List<CatalogColumn> orderedSource = source.stream()
        .sorted(Comparator.comparing(c -> key(c.name()))).toList();
    String definition = hash(new Evidence(model, orderedColumns, orderedMappings, orderedSource));
    Preview preview = new Preview(modelId, model.sourceDatasourceId(), model.sourceDatabase(),
        model.sourceTable(), definition,
        orderedSource.stream().map(CatalogColumn::name).toList(), List.copyOf(rows),
        mapped, unmapped, generated, orphan);
    return new State(preview, model, targets, saved, sources);
  }

  private String hash(Object evidence) {
    try {
      byte[] serialized = json.writeValueAsBytes(evidence);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
    } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("ODS 来源证据无法生成指纹", e);
    }
  }

  private static boolean technical(String column, Map<String, CatalogColumn> sources) {
    String name = key(column);
    return ("process_time".equals(name) || "event_time".equals(name))
        && !sources.containsKey(name);
  }

  private static boolean sameBinding(Model model, ModelingColumnMappingPO mapping) {
    return Objects.equals(model.sourceDatasourceId(), mapping.getSourceDatasourceId())
        && Objects.equals(model.sourceDatabase(), mapping.getSourceDatabase())
        && Objects.equals(model.sourceTable(), mapping.getSourceTable());
  }

  private static String key(String value) { return value.toLowerCase(Locale.ROOT).trim(); }

  private static ModelingException reject(String message) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, message);
  }
}
