package io.yak.ops.business.modeling.structure;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.modeling.api.ModelingStructureApi.ColumnInput;
import io.yak.ops.business.modeling.api.ModelingStructureApi.IndexInput;
import io.yak.ops.business.modeling.api.ModelingStructureApi.PartitionInput;
import io.yak.ops.business.modeling.api.ModelingStructureApi.SaveStructureRequest;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns a model's physical table structure rules: identifier-shaped table and
 * column names, case-insensitive column uniqueness, primary-key/index/partition
 * column references, and full-replace saves.
 */
@Component
public class ModelStructureService {

  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_$]{0,127}$");
  private static final String RESERVED_PRIMARY_INDEX_NAME = "PRIMARY";
  private static final String FIELD_ROLE_DIMENSION = "DIMENSION";
  private static final String FIELD_ROLE_MEASURE = "MEASURE";
  private static final Set<String> AGGREGATE_FUNCS =
      Set.of("SUM", "COUNT", "COUNT_DISTINCT", "MAX", "MIN", "AVG");

  private final ModelRepository modelRepository;
  private final ModelStructureRepository structureRepository;
  private final BusinessAuditService auditService;

  public ModelStructureService(
      ModelRepository modelRepository,
      ModelStructureRepository structureRepository,
      BusinessAuditService auditService) {
    this.modelRepository = modelRepository;
    this.structureRepository = structureRepository;
    this.auditService = auditService;
  }

  public StructureView get(Long modelId) {
    Model model = requireLiveModel(modelId);
    List<ColumnDefinition> columns = structureRepository.findColumns(modelId);
    List<IndexDefinition> indexes = structureRepository.findIndexes(modelId);
    return StructureView.of(
        model,
        structureRepository.findTableName(modelId).orElse(null),
        structureRepository.findTableComment(modelId).orElse(null),
        columns,
        StructureJson.readNameList(structureRepository.findPrimaryKeyJson(modelId).orElse(null)),
        indexes,
        new StructureView.PartitionView(
            structureRepository.findPartitionType(modelId).orElse(null),
            StructureJson.readNameList(structureRepository.findPartitionColumnsJson(modelId).orElse(null)),
            structureRepository.findPartitionExpr(modelId).orElse(null)),
        StructureJson.readProperties(structureRepository.findTablePropertiesJson(modelId).orElse(null)));
  }

  /** Reads a complete current structure using locking reads, even inside a transaction with an old MVCC view. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public StructureView getForUpdate(Long modelId) {
    Model model = modelRepository.findByIdForUpdate(modelId)
        .orElseThrow(() -> new ModelingException(
            ModelingErrorCode.NOT_FOUND, "模型不存在或已删除:" + modelId));
    var attributes = structureRepository.findStructureAttributesForUpdate(modelId)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND));
    List<ColumnDefinition> columns = structureRepository.findColumnsForUpdate(modelId);
    List<IndexDefinition> indexes = structureRepository.findIndexesForUpdate(modelId);
    return StructureView.of(
        model,
        attributes.tableName(),
        attributes.tableComment(),
        columns,
        StructureJson.readNameList(attributes.primaryKeyJson()),
        indexes,
        new StructureView.PartitionView(
            attributes.partitionType(), StructureJson.readNameList(attributes.partitionColumnsJson()),
            attributes.partitionExpr()),
        StructureJson.readProperties(attributes.tablePropertiesJson()));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public StructureView save(Long modelId, SaveStructureRequest request, String operator) {
    Model model = modelRepository.findByIdForUpdate(modelId)
        .orElseThrow(() -> new ModelingException(
            ModelingErrorCode.NOT_FOUND, "模型不存在或已删除:" + modelId));
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_STRUCTURE_SAVE",
                "Save modeling table structure",
                "MODELING_MODEL",
                String.valueOf(modelId),
                model.name(),
                "APPLICATION",
                Map.of("columnCount", request.columns() == null ? 0 : request.columns().size())));
    try {
      List<ValidationIssue> issues = validate(modelId, request);
      String errors =
          issues.stream()
              .filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR)
              .map(ValidationIssue::message)
              .collect(java.util.stream.Collectors.joining("；"));
      if (!errors.isEmpty()) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, errors);
      }
      String tableName = resolveTableName(request.tableName(), model);
      String tableComment = normalize(request.tableComment());
      List<ColumnDefinition> columns = toDefinitions(request.columns());
      Set<String> liveColumnNames = liveColumnNames(columns);
      List<String> primaryKey = resolvePrimaryKey(request.primaryKey(), liveColumnNames);
      List<IndexDefinition> indexes = resolveIndexes(request.indexes(), liveColumnNames);
      StructureView.PartitionView partition =
          resolvePartition(request.partition(), liveColumnNames);
      Map<String, String> tableProperties = resolveTableProperties(request.tableProperties());

      if (!structureRepository.updateTableInfo(modelId, tableName, tableComment)) {
        throw new ModelingException(ModelingErrorCode.UPDATE_FAILED);
      }
      structureRepository.replaceColumns(modelId, columns);
      if (!structureRepository.updateTableAttributes(
          modelId,
          StructureJson.writeNameList(primaryKey),
          partition.type(),
          StructureJson.writeNameList(partition.columns()),
          partition.expression(),
          StructureJson.writeProperties(tableProperties))) {
        throw new ModelingException(ModelingErrorCode.UPDATE_FAILED);
      }
      structureRepository.replaceIndexes(modelId, indexes);
      // 保存结构后重置为草稿(表示有未发布的变更)
      modelRepository.updateStatus(modelId, ModelStatus.DRAFT.name(), operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Modeling table structure saved",
          Map.of(
              "columnCount", columns.size(),
              "indexCount", indexes.size(),
              "primaryKeyColumns", primaryKey.size()),
          "表结构已保存");
      return StructureView.of(
          model, tableName, tableComment, columns, primaryKey, indexes, partition, tableProperties);
    } catch (RuntimeException exception) {
      audit.failure("MODELING_STRUCTURE_SAVE_FAILED", exception);
      throw exception;
    }
  }

  /**
   * Dialect validation (ticket 07): identifier/reserved-word checks, data-type
   * catalog membership, length/scale matching and pk/index/partition column
   * references. ERROR issues block save; WARNING issues only surface.
   */
  public List<ValidationIssue> validate(Long modelId, SaveStructureRequest request) {
    Model model = requireLiveModel(modelId);
    List<ValidationIssue> issues = new ArrayList<>();

    String tableName = request.tableName() == null ? "" : request.tableName().trim();
    if (StringUtils.hasText(tableName)
        && StructureDialectCatalog.isReservedWord(model.dialect(), tableName)) {
      issues.add(
          new ValidationIssue(
              ValidationIssue.Severity.WARNING,
              ValidationIssue.Scope.TABLE,
              tableName,
              "表名与 " + model.dialect() + " 保留字冲突：" + tableName));
    }

    List<ColumnDefinition> columns;
    try {
      columns = toDefinitions(request.columns());
    } catch (ModelingException exception) {
      issues.add(
          new ValidationIssue(
              ValidationIssue.Severity.ERROR,
              ValidationIssue.Scope.COLUMN,
              null,
              exception.getUserMessage()));
      return issues;
    }

    Set<String> liveColumnNames = liveColumnNames(columns);
    for (ColumnDefinition column : columns) {
      StructureDialectCatalog.OptionalSpec spec =
          StructureDialectCatalog.lookupType(model.dialect(), column.dataType());
      if (spec == null) {
        issues.add(
            new ValidationIssue(
                ValidationIssue.Severity.ERROR,
                ValidationIssue.Scope.COLUMN,
                column.columnName(),
                "类型 " + column.dataType() + " 不在 " + model.dialect() + " 类型目录中"));
        continue;
      }
      if (spec.lengthRequired() && column.length() == null) {
        issues.add(
            new ValidationIssue(
                ValidationIssue.Severity.ERROR,
                ValidationIssue.Scope.COLUMN,
                column.columnName(),
                "类型 " + spec.name() + " 需要指定长度"));
      }
      if (column.scale() != null) {
        if (!spec.scaleAllowed()) {
          issues.add(
              new ValidationIssue(
                  ValidationIssue.Severity.ERROR,
                  ValidationIssue.Scope.COLUMN,
                  column.columnName(),
                  "类型 " + spec.name() + " 不支持小数位"));
        } else if (column.length() != null && column.scale() >= column.length()) {
          issues.add(
              new ValidationIssue(
                  ValidationIssue.Severity.ERROR,
                  ValidationIssue.Scope.COLUMN,
                  column.columnName(),
                  "小数位必须小于长度/精度"));
        }
      }
      if (StructureDialectCatalog.isReservedWord(model.dialect(), column.columnName())) {
        issues.add(
            new ValidationIssue(
                ValidationIssue.Severity.WARNING,
                ValidationIssue.Scope.COLUMN,
                column.columnName(),
                "字段名与 " + model.dialect() + " 保留字冲突：" + column.columnName()));
      }
      String role = column.fieldRole();
      if (StringUtils.hasText(role)) {
        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
        if (!FIELD_ROLE_DIMENSION.equals(normalizedRole) && !FIELD_ROLE_MEASURE.equals(normalizedRole)) {
          issues.add(
              new ValidationIssue(
                  ValidationIssue.Severity.ERROR,
                  ValidationIssue.Scope.COLUMN,
                  column.columnName(),
                  "字段角色必须为 DIMENSION(维度)或 MEASURE(度量)：" + role));
        } else if (FIELD_ROLE_MEASURE.equals(normalizedRole)) {
          String func = column.aggregateFunc();
          if (!StringUtils.hasText(func)) {
            issues.add(
                new ValidationIssue(
                    ValidationIssue.Severity.ERROR,
                    ValidationIssue.Scope.COLUMN,
                    column.columnName(),
                    "度量字段(MEASURE)必须指定聚合函数"));
          } else if (!AGGREGATE_FUNCS.contains(func.trim().toUpperCase(Locale.ROOT))) {
            issues.add(
                new ValidationIssue(
                    ValidationIssue.Severity.ERROR,
                    ValidationIssue.Scope.COLUMN,
                    column.columnName(),
                    "聚合函数不合法：" + func + "(允许 " + AGGREGATE_FUNCS + ")"));
          }
        }
      }
    }

    try {
      Set<String> checked = liveColumnNames(columns);
      for (String column : request.primaryKey() == null ? List.<String>of() : request.primaryKey()) {
        requireColumnExists(column, "主键", checked);
      }
    } catch (ModelingException exception) {
      issues.add(
          new ValidationIssue(
              ValidationIssue.Severity.ERROR,
              ValidationIssue.Scope.PRIMARY_KEY,
              null,
              exception.getUserMessage()));
    }
    for (IndexInput input : request.indexes() == null ? List.<IndexInput>of() : request.indexes()) {
      try {
        Set<String> checked = liveColumnNames(columns);
        if (input.columns() == null || input.columns().isEmpty()) {
          throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "索引至少需要一个字段：" + input.indexName());
        }
        for (String column : input.columns()) {
          requireColumnExists(column, "索引 " + input.indexName(), checked);
        }
        if (RESERVED_PRIMARY_INDEX_NAME.equalsIgnoreCase(input.indexName() == null ? "" : input.indexName().trim())) {
          throw new ModelingException(
              ModelingErrorCode.INVALID_COLUMN, "索引名 PRIMARY 为保留名，请改用主键设置");
        }
      } catch (ModelingException exception) {
        issues.add(
            new ValidationIssue(
                ValidationIssue.Severity.ERROR,
                ValidationIssue.Scope.INDEX,
                input.indexName(),
                exception.getUserMessage()));
      }
    }
    try {
      Set<String> checked = liveColumnNames(columns);
      PartitionInput partition = request.partition();
      for (String column : partition == null || partition.columns() == null
          ? List.<String>of()
          : partition.columns()) {
        requireColumnExists(column, "分区", checked);
      }
    } catch (ModelingException exception) {
      issues.add(
          new ValidationIssue(
              ValidationIssue.Severity.ERROR,
              ValidationIssue.Scope.PARTITION,
              null,
              exception.getUserMessage()));
    }
    Map<String, String> requestProperties =
        request.tableProperties() == null ? Map.of() : request.tableProperties();
    for (Map.Entry<String, String> entry : requestProperties.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        issues.add(
            new ValidationIssue(
                ValidationIssue.Severity.ERROR,
                ValidationIssue.Scope.PROPERTY,
                null,
                "表属性键不能为空"));
      }
    }
    return issues;
  }

  /** Dialect of a live model; used by the editor's type dropdown (ticket 07). */
  public ModelDialect dialectOf(Long modelId) {
    return requireLiveModel(modelId).dialect();
  }

  /**
   * 44 治理回填:把某个字段的标准字段关联写到 ODS 模型上(只改单列,不触发整表替换)。
   * 治理结果沉淀在 ODS 层(契约 §3.4),下次派生直接继承。
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public boolean assignStandardField(
      Long modelId, String columnName, Long stdFieldId, String operator) {
    if (stdFieldId == null) {
      return false;
    }
    requireLiveModel(modelId);
    return structureRepository.updateColumnStdField(modelId, columnName, stdFieldId);
  }

  private Model requireLiveModel(Long modelId) {
    return modelRepository
        .findById(modelId)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
  }

  /** Blank table names fall back to the model code (DOMAIN.md). */
  private String resolveTableName(String tableName, Model model) {
    if (!StringUtils.hasText(tableName)) {
      return model.code();
    }
    String trimmed = tableName.trim();
    if (!IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
      throw new ModelingException(
          ModelingErrorCode.INVALID_TABLE_NAME,
          "表名仅允许字母、数字、下划线和 $，且以字母、数字或下划线开头：" + trimmed);
    }
    return trimmed;
  }

  private List<ColumnDefinition> toDefinitions(List<ColumnInput> inputs) {
    Map<String, String> seenNames = new HashMap<>();
    List<ColumnDefinition> definitions = new ArrayList<>();
    if (inputs == null) {
      return definitions;
    }
    for (ColumnInput input : inputs) {
      String name = input.columnName() == null ? "" : input.columnName().trim();
      if (!StringUtils.hasText(name)) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "字段名不能为空");
      }
      if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
        throw new ModelingException(
            ModelingErrorCode.INVALID_COLUMN, "字段名仅允许字母、数字、下划线和 $：" + name);
      }
      String duplicateOf =
          seenNames.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
      if (duplicateOf != null) {
        throw new ModelingException(
            ModelingErrorCode.INVALID_COLUMN, "字段名重复：" + name + "（与 " + duplicateOf + " 冲突）");
      }
      String dataType = input.dataType() == null ? "" : input.dataType().trim();
      if (!StringUtils.hasText(dataType)) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "字段类型不能为空：" + name);
      }
      definitions.add(
          new ColumnDefinition(
              null,
              name,
              dataType,
              input.length(),
              input.scale(),
              input.nullable() == null || input.nullable(),
              normalize(input.defaultValue()),
              normalize(input.comment()),
              normalize(input.businessDescription()),
              definitions.size(),
              input.stdTypeId(),
              input.stdNamingId(),
              input.stdCodeSetCode(),
              input.stdUnitId(),
              input.stdCaliberId(),
              input.stdSecurityId(),
              input.stdFieldId(),
              normalize(input.fieldRole()),
              normalize(input.aggregateFunc()),
              normalize(input.transformExpr())));
    }
    return definitions;
  }

  private Set<String> liveColumnNames(List<ColumnDefinition> columns) {
    Set<String> names = new HashSet<>();
    for (ColumnDefinition column : columns) {
      names.add(column.columnName().toLowerCase(Locale.ROOT));
    }
    return names;
  }

  private List<String> resolvePrimaryKey(List<String> primaryKey, Set<String> liveColumnNames) {
    if (primaryKey == null || primaryKey.isEmpty()) {
      return List.of();
    }
    List<String> resolved = new ArrayList<>();
    for (String column : primaryKey.stream().distinct().toList()) {
      requireColumnExists(column, "主键", liveColumnNames);
      resolved.add(column.trim());
    }
    return resolved;
  }

  private List<IndexDefinition> resolveIndexes(List<IndexInput> inputs, Set<String> liveColumnNames) {
    if (inputs == null || inputs.isEmpty()) {
      return List.of();
    }
    Map<String, String> seenNames = new HashMap<>();
    List<IndexDefinition> indexes = new ArrayList<>();
    for (IndexInput input : inputs) {
      String name = input.indexName() == null ? "" : input.indexName().trim();
      if (!StringUtils.hasText(name)) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "索引名不能为空");
      }
      if (RESERVED_PRIMARY_INDEX_NAME.equalsIgnoreCase(name)) {
        throw new ModelingException(
            ModelingErrorCode.INVALID_COLUMN, "索引名 PRIMARY 为保留名，请改用主键设置");
      }
      String duplicateOf =
          seenNames.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
      if (duplicateOf != null) {
        throw new ModelingException(
            ModelingErrorCode.INVALID_COLUMN, "索引名重复：" + name + "（与 " + duplicateOf + " 冲突）");
      }
      if (input.columns() == null || input.columns().isEmpty()) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "索引至少需要一个字段：" + name);
      }
      List<String> columns = new ArrayList<>();
      for (String column : input.columns().stream().distinct().toList()) {
        requireColumnExists(column, "索引 " + name, liveColumnNames);
        columns.add(column.trim());
      }
      indexes.add(
          new IndexDefinition(
              null,
              name,
              input.uniqueIndex() != null && input.uniqueIndex(),
              normalize(input.indexType()),
              columns));
    }
    return indexes;
  }

  private StructureView.PartitionView resolvePartition(
      PartitionInput input, Set<String> liveColumnNames) {
    if (input == null) {
      return new StructureView.PartitionView(null, List.of(), null);
    }
    List<String> columns = new ArrayList<>();
    for (String column : input.columns() == null ? List.<String>of() : input.columns()) {
      requireColumnExists(column, "分区", liveColumnNames);
      columns.add(column.trim());
    }
    boolean hasAny = StringUtils.hasText(input.type()) || !columns.isEmpty()
        || StringUtils.hasText(input.expression());
    if (!hasAny) {
      return new StructureView.PartitionView(null, List.of(), null);
    }
    return new StructureView.PartitionView(
        normalize(input.type()), columns, normalize(input.expression()));
  }

  private Map<String, String> resolveTableProperties(Map<String, String> properties) {
    if (properties == null || properties.isEmpty()) {
      return Map.of();
    }
    Map<String, String> resolved = new HashMap<>();
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "表属性键不能为空");
      }
      resolved.put(entry.getKey().trim(), normalize(entry.getValue()));
    }
    return resolved;
  }

  private void requireColumnExists(String column, String owner, Set<String> liveColumnNames) {
    String name = column == null ? "" : column.trim();
    if (!StringUtils.hasText(name) || !liveColumnNames.contains(name.toLowerCase(Locale.ROOT))) {
      throw new ModelingException(
          ModelingErrorCode.INVALID_COLUMN, owner + " 引用的字段不存在：" + column);
    }
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
