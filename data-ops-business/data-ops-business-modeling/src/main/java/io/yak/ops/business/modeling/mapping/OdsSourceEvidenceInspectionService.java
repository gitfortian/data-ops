package io.yak.ops.business.modeling.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.Model;
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
 * Current, fresh, read-only ODS source evidence; not a published-version
 * snapshot, write permit, data quality check or real execution receipt.
 */
@Service
@RequiredArgsConstructor
public class OdsSourceEvidenceInspectionService {
  private static final int MAX_COLUMNS = 1000;
  private final ModelRepository models;
  private final ModelStructureRepository structures;
  private final MappingRepository mappings;
  private final DataSourceCatalogReader catalog;
  private final ActionAuthorization authorization;
  private final ObjectMapper json;

  public record FieldEvidence(String targetColumn, String targetType, String sourceColumn,
                              String sourceType, String status, String reason) {}
  public record Inspection(Long modelId, String modelStatus, Long sourceDatasourceId,
                           String sourceDatabase, String sourceTable, String status,
                           String evidenceSha256, int targetCount, int sourceCount,
                           int mappingCount, List<FieldEvidence> fields, List<String> blockers) {}
  private record SourceRef(Long datasourceId, String database, String schema, String table) {}
  private record MappingFact(String target, Long datasourceId, String database, String schema, String table,
                             String source, String transform, Long standardFieldId) {}
  private record Evidence(Long modelId, String modelCode, String modelStatus,
                          String layerCode, SourceRef source, List<ColumnDefinition> columns,
                          List<MappingFact> mappings, List<CatalogColumn> sourceColumns) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Inspection inspect(Long modelId) {
    authorization.requirePermission(ModelingPermissionCode.READ);
    authorization.requirePermission(DataSourcePermissionCode.READ);
    if (modelId == null || modelId <= 0) throw invalid("无效的 ODS 模型");
    Model model = models.findById(modelId).orElseThrow(() ->
        new ModelingException(ModelingErrorCode.NOT_FOUND, "模型不存在或无权访问"));
    if (model.isRecycled() || !"ODS".equalsIgnoreCase(model.layerCode())) {
      throw invalid("仅支持项目内未回收的 ODS 物理模型来源核验");
    }
    List<ColumnDefinition> columns = structures.findColumns(modelId);
    List<ModelingColumnMappingPO> saved = mappings.listByModel(modelId);
    if (columns == null || saved == null || columns.size() > MAX_COLUMNS
        || saved.size() > MAX_COLUMNS) throw invalid("ODS 模型字段或已有映射不可核验");
    SourceRef binding = new SourceRef(model.sourceDatasourceId(),
        model.sourceDatabase(), model.sourceSchema(), model.sourceTable());
    if (model.sourceDatasourceId() == null || !StringUtils.hasText(model.sourceTable())) {
      return new Inspection(modelId, model.status().name(), model.sourceDatasourceId(),
          model.sourceDatabase(), model.sourceTable(), "NOT_CHECKABLE", null, columns.size(),
          0, saved.size(), List.of(), List.of("当前模型没有正式来源绑定，不能核验真实源字段"));
    }

    // Fresh catalog read is mandatory. Failure/empty must NOT be interpreted
    // as a missing source table, changed schema, or successful verification.
    List<CatalogColumn> sources = catalog.listColumnsFresh(
        model.sourceDatasourceId(), model.sourceDatabase(), model.sourceSchema(), model.sourceTable());
    if (sources == null || sources.isEmpty() || sources.size() > MAX_COLUMNS) {
      throw invalid("真实源表返回空或不可用的字段证据，请检查授权和连通性后重试");
    }
    Map<String, CatalogColumn> sourceByName = new HashMap<>();
    for (CatalogColumn c : sources) {
      if (c == null || sourceByName.putIfAbsent(key(c.name()), c) != null) {
        throw invalid("源表列身份重复或无效，不能形成可信核验结果");
      }
    }
    Map<String, ColumnDefinition> targetByName = new HashMap<>();
    for (ColumnDefinition c : columns) {
      if (c == null || !StringUtils.hasText(c.columnName())
          || targetByName.putIfAbsent(key(c.columnName()), c) != null) {
        throw invalid("模型目标字段重复或无效，不能形成可信核验结果");
      }
    }
    Map<String, ModelingColumnMappingPO> mappingByTarget = new HashMap<>();
    List<String> blockers = new ArrayList<>();
    for (ModelingColumnMappingPO row : saved) {
      if (row == null || !StringUtils.hasText(row.getTargetColumn())) {
        blockers.add("来源映射存在没有目标列身份的记录");
      } else if (mappingByTarget.putIfAbsent(key(row.getTargetColumn()), row) != null) {
        blockers.add("目标列存在重复来源映射：" + row.getTargetColumn());
      }
    }
    Set<String> referencedSources = new HashSet<>();
    List<FieldEvidence> fields = new ArrayList<>();
    for (ColumnDefinition target : columns) {
      String physicalName = target.columnName();
      ModelingColumnMappingPO mapping = mappingByTarget.get(key(physicalName));
      if (mapping == null) {
        if (technical(physicalName) && !sourceByName.containsKey(key(physicalName))) {
          fields.add(new FieldEvidence(physicalName, target.dataType(), null, null,
              "TECHNICAL", "平台生成字段，无真实来源映射"));
        } else {
          fields.add(new FieldEvidence(physicalName, target.dataType(), null, null,
              "UNMAPPED", "物理列未保存真实源字段引用"));
          blockers.add("物理列未映射：" + physicalName);
        }
        continue;
      }
      if (MappingService.isExpressionOnly(mapping)) {
        fields.add(new FieldEvidence(physicalName, target.dataType(), null, null,
            "EXPRESSION", "表达式映射，不依赖源字段"));
        continue;
      }
      String sourceName = mapping.getSourceColumn();
      if (!Objects.equals(model.sourceDatasourceId(), mapping.getSourceDatasourceId())
          || !Objects.equals(model.sourceDatabase(), mapping.getSourceDatabase())
          || !Objects.equals(model.sourceSchema(), mapping.getSourceSchema())
          || !Objects.equals(model.sourceTable(), mapping.getSourceTable())) {
        fields.add(new FieldEvidence(physicalName, target.dataType(), sourceName,
            null, "SOURCE_CONFLICT", "映射来源与模型正式绑定来源不一致"));
        blockers.add("历史映射来源冲突：" + physicalName);
        continue;
      }
      if (!StringUtils.hasText(sourceName)) {
        fields.add(new FieldEvidence(physicalName, target.dataType(), null, null,
            "MISSING_SOURCE", "映射缺少实际源列身份"));
        blockers.add("来源列缺失：" + physicalName);
        continue;
      }
      CatalogColumn source = sourceByName.get(key(sourceName));
      if (source == null) {
        fields.add(new FieldEvidence(physicalName, target.dataType(), sourceName, null,
            "MISSING_SOURCE", "最新授权源表不存在该列；不能推断源表已删除"));
        blockers.add("真实源列无法匹配：" + sourceName);
        continue;
      }
      if (!referencedSources.add(key(sourceName))) {
        fields.add(new FieldEvidence(physicalName, target.dataType(), source.name(),
            source.typeName(), "DUPLICATE_SOURCE", "同一真实源列被多个 ODS 字段引用"));
        blockers.add("ODS 来源镜像非一对一：" + sourceName);
        continue;
      }
      String status = "MATCHED";
      String reason = "正式来源身份与当前真实源列相符";
      if (StringUtils.hasText(mapping.getTransformExpr())) {
        status = "TRANSFORM_REVIEW";
        reason = "ODS 映射含转换表达式，需人工核实来源保真";
      } else if (StringUtils.hasText(source.typeName())
          && StringUtils.hasText(target.dataType())
          && !key(source.typeName()).equals(key(target.dataType()))) {
        status = "TYPE_REVIEW";
        reason = "真实源字段与当前物理列类型名不同；可能是方言别名，不能自动判定兼容";
      } else if (source.nullable() && Boolean.FALSE.equals(target.nullable())) {
        status = "NULLABILITY_REVIEW";
        reason = "真实源列允许 NULL，当前物理列不允许；需人工确认落地策略";
      }
      fields.add(new FieldEvidence(physicalName, target.dataType(), source.name(),
          source.typeName(), status, reason));
      if (!"MATCHED".equals(status)) blockers.add(reason + "：" + physicalName);
    }
    for (ModelingColumnMappingPO mapping : saved) {
      if (StringUtils.hasText(mapping.getTargetColumn())
          && !targetByName.containsKey(key(mapping.getTargetColumn()))) {
        blockers.add("历史孤儿映射：" + mapping.getTargetColumn());
        fields.add(new FieldEvidence(mapping.getTargetColumn(), null,
            mapping.getSourceColumn(), null, "ORPHAN_MAPPING", "目标物理列已不存在"));
      }
    }
    for (CatalogColumn source : sources) {
      if (!referencedSources.contains(key(source.name()))) {
        blockers.add("真实源列未覆盖：" + source.name());
        fields.add(new FieldEvidence(null, null, source.name(), source.typeName(),
            "UNREFERENCED_SOURCE", "当前模型没有该真实源列的唯一有效映射"));
      }
    }
    List<ColumnDefinition> orderedColumns = columns.stream()
        .sorted(Comparator.comparing(c -> key(c.columnName()))).toList();
    List<MappingFact> orderedMappings = saved.stream().filter(Objects::nonNull)
        .map(m -> new MappingFact(m.getTargetColumn(), m.getSourceDatasourceId(),
            m.getSourceDatabase(), m.getSourceSchema(), m.getSourceTable(), m.getSourceColumn(),
            m.getTransformExpr(), m.getStdProcessFieldId()))
        .sorted(Comparator.comparing(m -> m.target() == null ? "" : key(m.target())))
        .toList();
    List<CatalogColumn> orderedSources = sources.stream()
        .sorted(Comparator.comparing(c -> key(c.name()))).toList();
    String fingerprint = digest(new Evidence(model.id(), model.code(), model.status().name(),
        model.layerCode(), binding, orderedColumns, orderedMappings, orderedSources));
    return new Inspection(modelId, model.status().name(), model.sourceDatasourceId(),
        model.sourceDatabase(), model.sourceTable(),
        blockers.isEmpty() ? "CONSISTENT_NOW" : "NEEDS_REVIEW", fingerprint,
        columns.size(), sources.size(), saved.size(), List.copyOf(fields),
        List.copyOf(blockers));
  }

  private String digest(Object evidence) {
    try {
      byte[] value = json.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsBytes(evidence);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
      throw new IllegalStateException("无法生成当前 ODS 来源核验指纹", exception);
    }
  }

  private static boolean technical(String name) {
    return "process_time".equals(key(name)) || "event_time".equals(key(name));
  }
  private static String key(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }
  private static ModelingException invalid(String reason) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, reason);
  }
}
