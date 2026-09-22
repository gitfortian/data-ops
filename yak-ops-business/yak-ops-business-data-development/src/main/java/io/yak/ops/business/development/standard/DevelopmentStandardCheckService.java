package io.yak.ops.business.development.standard;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentStandardCheck;
import io.yak.ops.business.development.domain.DevelopmentStandardCheck.FieldCheck;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.service.SqlColumnLineageParser;
import io.yak.ops.business.semantic.api.StandardRecommendApi;
import io.yak.ops.business.semantic.api.StandardRecommendApi.NamingCheck;
import io.yak.ops.business.semantic.api.StandardRecommendApi.RecommendRequest;
import io.yak.ops.business.semantic.api.StandardRecommendApi.RecommendationReport;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Reuses the data-development lineage engine to harvest the output column names of one
 * editor SQL statement, then asks the semantic naming standard about each field.
 *
 * <p>The check is a non-blocking hint. Semantic failures degrade to {@code evaluated=false}
 * per field and never abort the request.
 */
@Service
public class DevelopmentStandardCheckService {

  private static final String SYNTHETIC_TARGET = "__yak_standard_check__";
  private static final int MAX_CHECKED_FIELDS = 200;

  private final DevelopmentNodeRepository nodeRepository;
  private final SqlColumnLineageParser columnParser;
  private final StandardRecommendApi standardRecommendApi;

  public DevelopmentStandardCheckService(
      DevelopmentNodeRepository nodeRepository,
      SqlColumnLineageParser columnParser,
      StandardRecommendApi standardRecommendApi) {
    this.nodeRepository = nodeRepository;
    this.columnParser = columnParser;
    this.standardRecommendApi = standardRecommendApi;
  }

  public DevelopmentStandardCheck check(Long nodeId, String taskType, String sql) {
    requireSqlNode(nodeId, taskType);
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("SQL 不能为空");
    }

    List<OutputField> fields;
    String parseError = null;
    try {
      fields = outputFields(stripTerminalSemicolon(sql));
    } catch (RuntimeException exception) {
      fields = List.of();
      parseError = safeMessage(exception);
    }

    boolean truncated = fields.size() > MAX_CHECKED_FIELDS;
    List<FieldCheck> items = new ArrayList<>();
    for (OutputField field : fields) {
      if (items.size() >= MAX_CHECKED_FIELDS) break;
      items.add(checkField(field));
    }
    return new DevelopmentStandardCheck(List.copyOf(items), fields.size(), truncated, parseError);
  }

  /**
   * Statements with a physical write target expose it directly; a bare SELECT carries none, so it
   * is wrapped into a synthetic CTAS the same way the dataset projection analyzer does.
   */
  private List<OutputField> outputFields(String sql) {
    List<OutputField> fields = outputFields(columnParser.parse(sql), null);
    if (!fields.isEmpty()) return fields;
    return outputFields(
        columnParser.parse("CREATE TABLE " + SYNTHETIC_TARGET + " AS " + sql), SYNTHETIC_TARGET);
  }

  private List<OutputField> outputFields(SqlColumnLineageParser.ParseResult parsed, String target) {
    Set<String> seen = new LinkedHashSet<>();
    List<OutputField> fields = new ArrayList<>();
    for (SqlColumnLineageParser.ColumnMapping mapping : parsed.mappings()) {
      String name = mapping.targetColumnName();
      if (name == null || name.isBlank()) continue;
      if (target != null
          && (mapping.targetTable() == null
              || !target.equalsIgnoreCase(mapping.targetTable().canonicalName()))) {
        continue;
      }
      if (seen.add(name.toLowerCase(Locale.ROOT))) {
        fields.add(new OutputField(name, mapping.outputOrdinal()));
      }
    }
    return List.copyOf(fields);
  }

  private FieldCheck checkField(OutputField field) {
    RecommendationReport report;
    try {
      report = standardRecommendApi.recommend(new RecommendRequest(field.name(), null, "UNKNOWN"));
    } catch (RuntimeException ignored) {
      return unevaluated(field);
    }
    NamingCheck naming = report == null ? null : report.naming();
    if (naming == null || !naming.evaluated()) {
      return unevaluated(field);
    }
    return new FieldCheck(
        field.name(),
        true,
        naming.matched(),
        naming.standardId(),
        naming.code(),
        naming.name(),
        naming.ruleExpr());
  }

  private static FieldCheck unevaluated(OutputField field) {
    return new FieldCheck(field.name(), false, false, null, null, null, null);
  }

  private void requireSqlNode(Long nodeId, String taskType) {
    if (nodeId == null || nodeId <= 0L) throw new IllegalArgumentException("节点 ID 非法");
    if (taskType == null || !"SQL".equalsIgnoreCase(taskType.trim())) {
      throw new IllegalArgumentException("标准符合度检查 taskType 必须为 SQL");
    }
    DevelopmentNode node = nodeRepository.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + nodeId));
    if (!"SQL".equalsIgnoreCase(node.type())) {
      throw new IllegalArgumentException("只有 SQL 节点支持标准符合度检查：" + node.type());
    }
  }

  private static String stripTerminalSemicolon(String sql) {
    String value = sql.trim();
    while (value.endsWith(";")) {
      value = value.substring(0, value.length() - 1).trim();
    }
    if (value.isEmpty()) throw new IllegalArgumentException("SQL 不能为空");
    return value;
  }

  private static String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "unknown parser error" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  private record OutputField(String name, int outputOrdinal) {}
}
