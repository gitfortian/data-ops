package io.yak.ops.business.metric.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metric.api.MetricDraftQueryApi;
import io.yak.ops.business.metric.domain.MetricQualifier;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.domain.MetricType;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.metric.support.MetricSnapshotDigest;
import io.yak.ops.business.modeling.api.ModelSuggestionQueryApi;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MetricDraftQueryAdapter implements MetricDraftQueryApi {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<String> AGGREGATIONS = Set.of("SUM", "COUNT", "COUNT_DISTINCT", "AVG", "MIN", "MAX");
  private static final Set<String> SYMBOLS = Set.of("ADD", "SUB", "MUL", "DIV", "LPAREN", "RPAREN");
  private final MetricRepository metrics;
  private final ObjectProvider<ModelSuggestionQueryApi> models;
  private final ActionAuthorization authorization;

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Context prepare(Input input) {
    authorization.requirePermission(MetricPermissionCode.READ);
    if (input == null) throw new IllegalArgumentException("缺少指标草稿目标");
    String existingDefinition = "new";
    if (input.metricId() != null) {
      var existing = metrics.findById(input.metricId()).orElseThrow(() -> new IllegalArgumentException("指标不可读取"));
      if (existing.version() != input.version() || !existing.metricType().name().equals(input.metricType())) {
        throw new IllegalArgumentException("指标版本或类型已变化");
      }
      existingDefinition = String.valueOf(existing.version());
    }
    var upstream = new ArrayList<Upstream>();
    Long modelId = input.modelId();
    for (long id : input.upstreamIds()) {
      if (Long.valueOf(id).equals(input.metricId())) throw new IllegalArgumentException("指标不能引用自身");
      var source = metrics.findById(id).orElseThrow(() -> new IllegalArgumentException("上游指标不可读取"));
      if (source.status() != MetricStatus.ENABLED || source.metricType() == MetricType.COMPOSITE
          || ("DERIVED".equals(input.metricType()) && source.metricType() != MetricType.ATOMIC)) {
        throw new IllegalArgumentException("上游指标类型或状态不适用");
      }
      // The original formula editor distinguishes numeric constants from metric codes.
      // Reject an ambiguous handoff instead of producing a draft that cannot be saved as REF.
      if ("COMPOSITE".equals(input.metricType()) && (source.metricCode() == null
          || !source.metricCode().matches("[A-Za-z0-9_]{1,64}") || source.metricCode().matches("[0-9]+"))) {
        throw new IllegalArgumentException("所选上游编码不能在原公式编辑器中明确表示指标引用，请先核对指标编码");
      }
      if ("DERIVED".equals(input.metricType())) modelId = source.modelId();
      upstream.add(new Upstream(id, source.version(), source.metricCode(), source.metricName(), source.metricType().name(),
          bounded(source.measureExpr(), 512), bounded(source.filterExpr(), 1024)));
    }
    List<Field> fields = List.of();
    String modelDefinition = "none";
    if (!"COMPOSITE".equals(input.metricType())) {
      if (modelId == null || modelId <= 0) throw new IllegalArgumentException("来源模型未就绪");
      var api = models.getIfAvailable();
      if (api == null) throw new IllegalStateException("模型字段辅助读取未装配");
      var source = api.fields(modelId);
      if (source == null || source.modelId() != modelId || source.definition() == null || !source.definition().matches("[a-f0-9]{64}")
          || source.fields() == null || source.fields().isEmpty() || source.fields().size() > 100) throw new IllegalStateException("模型字段上下文不可用");
      fields = source.fields().stream().map(f -> new Field(f.name(), f.type(), f.description())).toList();
      modelDefinition = source.definition();
    }
    try {
      String digest = MetricSnapshotDigest.sha256(JSON.writeValueAsString(List.of(input, existingDefinition, modelDefinition, fields, upstream)));
      return new Context(digest, fields, List.copyOf(upstream));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("草稿上下文不可用"); }
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Draft validate(Input input, String expectedDefinition, Draft draft) {
    var source = prepare(input);
    if (!source.definition().equals(expectedDefinition)) throw new IllegalArgumentException("指标依赖或模型结构已变化，请重新生成");
    if (draft == null || !text(draft.name(), 128) || !text(draft.description(), 512)
        || !List.of("DAY", "WEEK", "MONTH").contains(draft.period() == null ? "" : draft.period())
        || draft.qualifiers() == null || draft.qualifiers().size() > 5 || draft.tokens() == null || draft.tokens().size() > 31) invalid();
    if ("ATOMIC".equals(input.metricType())) {
      if (!AGGREGATIONS.contains(draft.aggregation() == null ? "" : draft.aggregation()) || !field(source, draft.field())
          || !draft.tokens().isEmpty() || !draft.qualifiers().isEmpty()) invalid();
    } else if ("DERIVED".equals(input.metricType())) {
      if (draft.aggregation() != null || draft.field() != null || !draft.tokens().isEmpty() || draft.qualifiers().isEmpty()) invalid();
      for (var q : draft.qualifiers()) {
        if (q == null || !field(source, q.field()) || q.op() == null || !MetricQualifier.OPS.contains(q.op()) || !text(q.value(), 128)) invalid();
        MetricQualifier.compile(new MetricQualifier(q.field(), q.op(), q.value()));
      }
    } else {
      if (draft.aggregation() != null || draft.field() != null || !draft.qualifiers().isEmpty() || draft.tokens().isEmpty()) invalid();
      int depth = 0; int references = 0; boolean operand = true;
      for (var token : draft.tokens()) {
        if (token == null || token.operator() == null) invalid();
        String op = token.operator();
        if ("REF".equals(op)) {
          if (!operand || token.metricId() == null || source.upstream().stream().noneMatch(u -> u.id() == token.metricId())) invalid();
          references++; operand = false;
        } else {
          if (!SYMBOLS.contains(op) || token.metricId() != null) invalid();
          if ("LPAREN".equals(op)) { if (!operand) invalid(); depth++; }
          else if ("RPAREN".equals(op)) { if (operand || depth == 0) invalid(); depth--; }
          else { if (operand) invalid(); operand = true; }
        }
      }
      if (operand || depth != 0 || references == 0) invalid();
    }
    return draft;
  }
  private static boolean field(Context source, String name) { return name != null && source.fields().stream().anyMatch(f -> f.name().equals(name)); }
  private static boolean text(String value, int max) { return value != null && !value.isBlank() && value.length() <= max; }
  private static String bounded(String value, int max) {
    if (value != null && value.length() > max) throw new IllegalArgumentException("上游定义超过草稿范围");
    return value == null ? "" : value;
  }
  private static void invalid() { throw new IllegalArgumentException("指标草稿不符合当前类型、字段或依赖约束"); }
}
