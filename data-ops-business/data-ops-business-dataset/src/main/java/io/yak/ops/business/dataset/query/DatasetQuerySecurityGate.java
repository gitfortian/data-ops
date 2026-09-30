package io.yak.ops.business.dataset.query;

import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetQueryColumnBinding;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetQuerySubject;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway.Analysis;
import io.yak.ops.business.dataset.gateway.lineage.DatasetProjectionAnalyzerGateway.ProjectionMapping;
import io.yak.ops.business.dataset.gateway.security.DatasetSecurityGateway;
import io.yak.ops.business.dataset.gateway.security.DatasetSecurityGateway.Classification;
import io.yak.ops.business.dataset.gateway.security.DatasetSecurityGateway.Decision;
import io.yak.ops.business.dataset.gateway.security.DatasetSecurityGateway.MaskingInstruction;
import io.yak.ops.business.dataset.query.DatasetSourceQueryAdapter.SourceDescriptor;
import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Enforces Security decisions against physical SQL lineage before Dataset source execution. */
@Component
public class DatasetQuerySecurityGate {

  private static final int SENSITIVE_RANK = 3;
  private static final String AUDIT_SOURCE = "DATASET_QUERY";

  private final DatasetProjectionAnalyzerGateway projectionAnalyzer;
  private final DatasetSecurityGateway securityGateway;

  public DatasetQuerySecurityGate(
      DatasetProjectionAnalyzerGateway projectionAnalyzer,
      DatasetSecurityGateway securityGateway) {
    this.projectionAnalyzer = projectionAnalyzer;
    this.securityGateway = securityGateway;
  }

  public AccessPlan authorize(
      SourceDescriptor source,
      List<DatasetField> fields,
      DatasetQueryRequest request,
      DatasetQuerySubject subject) {
    if (subject == null || !StringUtils.hasText(subject.sourceIdentity())) {
      throw rejected("Dataset 查询缺少已认证主体，无法执行数据安全裁决");
    }
    if (source == null || !StringUtils.hasText(source.dataSourceId()) || !StringUtils.hasText(source.sql())) {
      throw rejected("Dataset 来源缺少物理数据源或 SQL，无法校验安全策略");
    }
    if (!source.dataSourceId().trim().matches("[0-9]+")) {
      throw rejected("Dataset 物理数据源 ID 无法映射到 Security 资源键");
    }

    Analysis analysis = projectionAnalyzer.analyze(source.dataSourceId(), source.sql());
    if (analysis == null || !analysis.available() || analysis.result() == null) {
      throw rejected("SQL 投影血缘不可用，无法验证物理数据的访问策略");
    }
    if (analysis.result().unresolvedReferenceCount() > 0) {
      throw rejected("SQL 存在未解析的物理字段，数据安全检查无法完成");
    }

    Map<String, DatasetField> fieldsById = new LinkedHashMap<>();
    Map<String, DatasetField> fieldsByPhysicalName = new LinkedHashMap<>();
    for (DatasetField field : fields == null ? List.<DatasetField>of() : fields) {
      if (field == null || !StringUtils.hasText(field.fieldId()) || !StringUtils.hasText(field.physicalName())) {
        continue;
      }
      fieldsById.put(field.fieldId(), field);
      fieldsByPhysicalName.put(normalize(field.physicalName()), field);
    }
    if (fieldsById.isEmpty()) {
      throw rejected("Dataset 没有可映射的字段定义，数据安全检查无法完成");
    }

    boolean rawMode = request == null
        || ((request.dimensions() == null || request.dimensions().isEmpty())
            && (request.metrics() == null || request.metrics().isEmpty()));
    Set<String> returnedFieldIds = new LinkedHashSet<>();
    Set<String> accessedFieldIds = new LinkedHashSet<>();
    if (rawMode) {
      returnedFieldIds.addAll(fieldsById.keySet());
    } else {
      if (request.dimensions() != null) returnedFieldIds.addAll(request.dimensions());
      if (request.metrics() != null) {
        request.metrics().stream().filter(metric -> metric != null).map(metric -> metric.fieldId())
            .filter(StringUtils::hasText).forEach(returnedFieldIds::add);
      }
    }
    accessedFieldIds.addAll(returnedFieldIds);
    if (request != null) {
      if (request.filters() != null) {
        request.filters().stream().filter(filter -> filter != null).map(filter -> filter.fieldId())
            .filter(StringUtils::hasText).forEach(accessedFieldIds::add);
      }
      if (request.sorts() != null) {
        request.sorts().stream().filter(sort -> sort != null).map(sort -> sort.fieldId())
            .filter(StringUtils::hasText).forEach(accessedFieldIds::add);
      }
    }
    if (accessedFieldIds.isEmpty()) {
      throw rejected("Dataset 查询没有可校验的输出字段");
    }
    for (String fieldId : accessedFieldIds) {
      if (!fieldsById.containsKey(fieldId)) {
        throw rejected("Dataset 查询字段不存在，无法校验安全策略：" + fieldId);
      }
    }

    Map<String, List<ProjectionMapping>> mappingsByOutput = new HashMap<>();
    for (ProjectionMapping mapping : analysis.result().mappings()) {
      if (mapping != null && StringUtils.hasText(mapping.outputColumnName())) {
        mappingsByOutput.computeIfAbsent(normalize(mapping.outputColumnName()), ignored -> new ArrayList<>())
            .add(mapping);
      }
    }

    Map<String, SourceAccess> sourceAccess = new LinkedHashMap<>();
    Map<String, MaskingInstruction> masksByFieldId = new LinkedHashMap<>();
    Map<String, MaskingInstruction> masksByRawOutput = new LinkedHashMap<>();
    Map<String, Set<String>> fieldSources = new LinkedHashMap<>();
    Map<String, String> physicalNamesByFieldId = new LinkedHashMap<>();
    for (String fieldId : accessedFieldIds) {
      DatasetField field = fieldsById.get(fieldId);
      physicalNamesByFieldId.put(fieldId, field.physicalName());
      List<ProjectionMapping> mappings = mappingsByOutput.get(normalize(field.physicalName()));
      if (mappings == null || mappings.isEmpty()) {
        throw rejected("Dataset 字段无法映射到物理列，安全检查已拒绝查询：" + field.physicalName());
      }
      for (ProjectionMapping mapping : mappings) {
        if (mapping.sourceTable() == null
            || !StringUtils.hasText(mapping.sourceTable().tableName())
            || !StringUtils.hasText(mapping.sourceColumnName())) {
          throw rejected("SQL 血缘映射缺少物理表或字段，安全检查已拒绝查询：" + field.physicalName());
        }
        String database = StringUtils.hasText(mapping.sourceTable().databaseName())
            ? mapping.sourceTable().databaseName()
            : mapping.sourceTable().schemaName();
        if (!StringUtils.hasText(database)) {
          throw rejected("SQL 血缘映射缺少物理库名，安全检查已拒绝查询：" + field.physicalName());
        }
        String objectKey = securityGateway.columnObjectKey(
            source.dataSourceId(), database, mapping.sourceTable().tableName(), mapping.sourceColumnName());
        SourceAccess access = sourceAccess.get(objectKey);
        if (access == null) {
          access = authorizeSource(subject.sourceIdentity(), subject.roles(), objectKey);
          sourceAccess.put(objectKey, access);
        }
        access.fieldIds().add(fieldId);
        fieldSources.computeIfAbsent(fieldId, ignored -> new LinkedHashSet<>()).add(objectKey);
        if (returnedFieldIds.contains(fieldId) && access.masking().required()) {
          if (rawMode) {
            mergeMask(masksByRawOutput, normalize(field.physicalName()), access.masking());
          } else {
            mergeMask(masksByFieldId, fieldId, access.masking());
          }
        }
      }
    }
    return new AccessPlan(
        rawMode, sourceAccess, masksByFieldId, masksByRawOutput, fieldSources,
        physicalNamesByFieldId, subject.sourceIdentity());
  }

  public DatasetQueryResult applyAndRecord(AccessPlan plan, DatasetQueryResult result) {
    Map<Integer, MaskingInstruction> indexMasks = new LinkedHashMap<>();
    Set<String> maskedFieldIds = new HashSet<>();
    Set<String> foundFieldIds = new HashSet<>();
    Map<String, DatasetQueryColumnBinding> bindingsByOutput = new HashMap<>();
    for (DatasetQueryColumnBinding binding : result.bindings()) {
      if (binding != null && StringUtils.hasText(binding.key())) {
        bindingsByOutput.put(normalize(binding.key()), binding);
      }
    }

    for (int index = 0; index < result.columns().size(); index++) {
      SqlExecutionColumn column = result.columns().get(index);
      MaskingInstruction directive = null;
      String fieldId = null;
      if (plan.rawMode()) {
        if (!plan.physicalNamesByFieldId().values().stream().map(DatasetQuerySecurityGate::normalize)
            .anyMatch(name -> name.equals(normalize(column.name())) || name.equals(normalize(column.label())))) {
          throw rejected("查询结果包含未登记字段，已拒绝返回数据");
        }
        directive = plan.masksByRawOutput().get(normalize(column.name()));
        if (directive == null) directive = plan.masksByRawOutput().get(normalize(column.label()));
      } else {
        DatasetQueryColumnBinding binding = bindingsByOutput.get(normalize(column.name()));
        if (binding == null) binding = bindingsByOutput.get(normalize(column.label()));
        if (binding == null) {
          throw rejected("查询结果包含未登记输出列，已拒绝返回数据");
        }
        fieldId = binding.fieldId();
        foundFieldIds.add(fieldId);
        directive = plan.masksByFieldId().get(fieldId);
      }
      if (directive != null && directive.required()) {
        indexMasks.put(index, directive);
        if (fieldId != null) maskedFieldIds.add(fieldId);
      }
    }

    Set<String> expectedMaskedFields = plan.rawMode()
        ? plan.masksByRawOutput().keySet()
        : plan.masksByFieldId().keySet();
    if (!plan.rawMode() && !foundFieldIds.containsAll(expectedMaskedFields)) {
      throw rejected("查询结果缺少需脱敏的字段，已阻止返回未验证数据");
    }
    if (plan.rawMode()) {
      Set<String> returnedColumnNames = result.columns().stream()
          .flatMap(column -> java.util.stream.Stream.of(column.name(), column.label()))
          .filter(StringUtils::hasText).map(DatasetQuerySecurityGate::normalize)
          .collect(java.util.stream.Collectors.toSet());
      if (!returnedColumnNames.containsAll(expectedMaskedFields)) {
        throw rejected("查询结果缺少需脱敏的字段，已阻止返回未验证数据");
      }
    }

    DatasetQueryResult secured = result;
    if (!indexMasks.isEmpty()) {
      List<List<Object>> rows = new ArrayList<>(result.rows().size());
      for (List<Object> row : result.rows()) {
        List<Object> cells = new ArrayList<>(row);
        indexMasks.forEach((index, directive) -> {
          if (index >= cells.size()) {
            throw rejected("查询结果列与字段定义不一致，已拒绝返回数据");
          }
          Object value = cells.get(index);
          cells.set(index, value == null ? null
              : securityGateway.mask(String.valueOf(value), directive));
        });
        rows.add(cells);
      }
      List<SqlExecutionColumn> columns = new ArrayList<>(result.columns());
      for (Integer index : indexMasks.keySet()) {
        SqlExecutionColumn column = columns.get(index);
        columns.set(index, new SqlExecutionColumn(
            column.name(), column.label(), "VARCHAR", Types.VARCHAR, true));
      }
      List<DatasetQueryColumnBinding> bindings = result.bindings().stream()
          .map(binding -> {
            boolean masked = plan.masksByFieldId().containsKey(binding.fieldId());
            return masked
                ? new DatasetQueryColumnBinding(binding.key(), binding.fieldId(), binding.displayName(),
                    DatasetFieldDataType.STRING, binding.aggregation())
                : binding;
          })
          .toList();
      secured = new DatasetQueryResult(
          result.queryId(), result.datasetId(), result.datasetVersionId(), result.datasetVersionNo(),
          bindings, columns, rows, result.returnedRows(), result.truncated(), result.elapsedMillis());
    }

    boolean hasRows = result.returnedRows() > 0;
    for (SourceAccess access : plan.sourceAccess().values()) {
      boolean masked = hasRows && access.fieldIds().stream().anyMatch(fieldId -> {
        boolean returned = plan.rawMode() || plan.masksByFieldId().containsKey(fieldId);
        boolean outputMasked = plan.rawMode()
            ? plan.masksByRawOutput().containsKey(normalize(plan.physicalNamesByFieldId().get(fieldId)))
            : maskedFieldIds.contains(fieldId);
        return returned && outputMasked
            && plan.fieldSources().getOrDefault(fieldId, Set.of()).contains(access.objectKey());
      });
      securityGateway.recordAccess(
          plan.actor(), access.objectKey(), "READ", access.decision(), masked, AUDIT_SOURCE);
    }
    return secured;
  }

  private SourceAccess authorizeSource(String actor, List<String> roles, String objectKey) {
    Decision decision = securityGateway.decide(actor, roles, objectKey, "READ");
    if (!decision.allowed()) {
      securityGateway.recordAccess(actor, objectKey, "READ", decision, false, AUDIT_SOURCE);
      throw rejected("数据安全策略拒绝 Dataset 查询：" + decision.decision());
    }
    Classification classification = securityGateway.classify(objectKey);
    MaskingInstruction directive = securityGateway.resolveMasking(objectKey);
    if (decision.maskingRequired() != directive.required()) {
      Decision denied = denied(decision);
      securityGateway.recordAccess(actor, objectKey, "READ", denied, false, AUDIT_SOURCE);
      throw rejected("脱敏裁决与执行指令不一致，已拒绝 Dataset 查询");
    }
    if (classification != null && classification.active()
        && classification.rank() != null && classification.rank() >= SENSITIVE_RANK
        && !directive.required()) {
      Decision denied = denied(decision);
      securityGateway.recordAccess(actor, objectKey, "READ", denied, false, AUDIT_SOURCE);
      throw rejected("高敏感字段缺少可执行脱敏策略，已拒绝 Dataset 查询");
    }
    if (directive.required() && !StringUtils.hasText(directive.algorithmCode())) {
      Decision denied = denied(decision);
      securityGateway.recordAccess(actor, objectKey, "READ", denied, false, AUDIT_SOURCE);
      throw rejected("脱敏指令缺少算法编码，已拒绝 Dataset 查询");
    }
    return new SourceAccess(objectKey, decision, directive);
  }

  private static Decision denied(Decision allowedDecision) {
    return new Decision(false, "DENY", allowedDecision.matchedPolicyId(), false, null);
  }

  private static void mergeMask(
      Map<String, MaskingInstruction> target, String key, MaskingInstruction value) {
    MaskingInstruction existing = target.putIfAbsent(key, value);
    if (existing != null
        && (!existing.algorithmCode().equals(value.algorithmCode())
            || !java.util.Objects.equals(existing.parameters(), value.parameters()))) {
      throw rejected("同一 Dataset 输出字段对应冲突的脱敏策略，已拒绝查询：" + key);
    }
  }

  private static DatasetQueryRejectedException rejected(String message) {
    return new DatasetQueryRejectedException(message, new IllegalStateException(message));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  public record AccessPlan(
      boolean rawMode,
      Map<String, SourceAccess> sourceAccess,
      Map<String, MaskingInstruction> masksByFieldId,
      Map<String, MaskingInstruction> masksByRawOutput,
      Map<String, Set<String>> fieldSources,
      Map<String, String> physicalNamesByFieldId,
      String actor) {
  }

  public record SourceAccess(
      String objectKey,
      Decision decision,
      MaskingInstruction masking,
      Set<String> fieldIds) {
    private SourceAccess(String objectKey, Decision decision, MaskingInstruction masking) {
      this(objectKey, decision, masking, new LinkedHashSet<>());
    }
  }
}
