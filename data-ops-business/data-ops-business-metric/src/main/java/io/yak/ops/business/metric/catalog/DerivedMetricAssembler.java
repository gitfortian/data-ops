package io.yak.ops.business.metric.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricQualifier;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 派生指标自动组装器(02):原子表达式 + 结构化限定条件 → 派生的
 * measureExpr(继承原子)/filterExpr(原子 filter AND 限定条件)/modelId(继承原子),
 * 落库后派生具备完整取数要素——建模反推与 03 试算直接消费。
 * 存量兼容:qualifiersJson 为空(自由文本 dimConstraint)时不组装,登记式行为不变。
 */
@Component
public class DerivedMetricAssembler {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** 单条限定条件是否非空(前端 Form.List 的占位空行不算限定)。 */
  public static boolean isBlank(MetricQualifier q) {
    return q == null
        || (!StringUtils.hasText(q.field()) && !StringUtils.hasText(q.op()) && !StringUtils.hasText(q.value()));
  }

  /** 解析 qualifiers_json;非法 JSON 抛 INVALID_COMPOSITION。 */
  public List<MetricQualifier> parse(String qualifiersJson) {
    if (!StringUtils.hasText(qualifiersJson)) {
      return List.of();
    }
    try {
      List<MetricQualifier> parsed =
          OBJECT_MAPPER.readValue(qualifiersJson, new TypeReference<List<MetricQualifier>>() {});
      return parsed == null ? List.of() : parsed;
    } catch (JsonProcessingException e) {
      throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
          "限定条件 JSON 不合法: " + e.getMessage());
    }
  }

  /** 编译为一条 AND 谓词串(03 试算/展示消费契约);无限定返回 null。 */
  public String compilePredicates(List<MetricQualifier> qualifiers) {
    List<MetricQualifier> effective =
        qualifiers == null ? List.of() : qualifiers.stream().filter(q -> !isBlank(q)).toList();
    return MetricQualifier.compileAll(effective);
  }

  /**
   * 把原子+限定组装进派生定义。仅在 DERIVED 且 qualifiersJson 非空时由服务层调用。
   *
   * @throws MetricException 原子缺 measureExpr 时拒绝组装(防产出口空派生)
   */
  public Metric assemble(Metric derived, Metric atomic, List<MetricQualifier> qualifiers) {
    if (!StringUtils.hasText(atomic.measureExpr())) {
      throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
          "引用的原子指标缺少度量表达式,无法自动组装派生定义: " + atomic.metricCode());
    }
    String predicates = compilePredicates(qualifiers);
    String filter = predicates == null
        ? atomic.filterExpr()
        : (StringUtils.hasText(atomic.filterExpr())
            ? atomic.filterExpr().trim() + " AND " + predicates
            : predicates);
    return new Metric(
        derived.id(), derived.metricCode(), derived.metricName(),
        derived.domainId() != null ? derived.domainId() : atomic.domainId(),
        derived.processId() != null ? derived.processId() : atomic.processId(),
        derived.metricType(),
        derived.caliberId() != null ? derived.caliberId() : atomic.caliberId(),
        StringUtils.hasText(derived.calRule()) ? derived.calRule() : atomic.calRule(),
        atomic.measureExpr(),
        filter,
        derived.dimModelIds(), derived.refMetricId(),
        predicates != null ? predicates : derived.dimConstraint(),
        derived.qualifiersJson(),
        atomic.modelId(),
        derived.statDimensions(), derived.statPeriod(),
        derived.unitId() != null ? derived.unitId() : atomic.unitId(),
        derived.businessDesc(), derived.owner(),
        derived.status(), derived.version(),
        derived.createdBy(), derived.updatedBy(),
        derived.createTime(), derived.updateTime());
  }
}
