package io.yak.ops.business.metric.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metric.api.MetricExplanationQueryApi;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.business.metric.support.MetricSnapshotDigest;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MetricExplanationQueryAdapter implements MetricExplanationQueryApi {
  private final MetricRepository metrics;
  private final MetricVersionRepository versions;
  private final ActionAuthorization authorization;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<String> KEYS = List.of("metricCode", "metricName", "metricType", "domainId", "processId",
      "caliberId", "calRule", "measureExpr", "filterExpr", "dimModelIds", "refMetricId", "refMetricVersion",
      "dimConstraint", "qualifiersJson", "modelId", "statDimensions", "statPeriod", "unitId", "businessDesc", "compositions");
  private static final List<String> LABELS = List.of("指标编码", "指标名称", "指标类型", "业务域 ID", "业务过程 ID",
      "口径标准 ID", "口径规则", "度量表达式", "过滤条件", "维度模型 ID", "引用指标 ID", "引用指标版本（0 为未记录）",
      "维度约束", "限定条件", "来源模型 ID", "统计维度", "统计周期", "单位 ID", "原业务说明", "组合项与引用版本（0 为未记录）");

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Context require(long metricId, int version) {
    return read(metricId, version, true);
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Context requireSnapshot(long metricId, int version) {
    return read(metricId, version, false);
  }

  private Context read(long metricId, int version, boolean requireCurrent) {
    authorization.requirePermission(MetricPermissionCode.READ);
    if (metricId <= 0 || version <= 0) throw new IllegalArgumentException("指标版本无效");
    var metric = metrics.findById(metricId).orElseThrow(() -> new IllegalArgumentException("指标不存在或不可读取"));
    if (requireCurrent && metric.version() != version) throw new IllegalArgumentException("指标已产生新版本，请重新打开编辑器");
    var snapshot = versions.findByMetricAndVersion(metricId, version);
    if (snapshot == null || snapshot.getId() == null || snapshot.getSnapshot() == null || snapshot.getSnapshot().length() > 65536) {
      throw new IllegalStateException("指标版本快照不可用或超过解释范围");
    }
    try {
      var node = JSON.readTree(snapshot.getSnapshot());
      if (node == null || !node.isObject() || !node.path("metricName").isTextual()
          || node.path("metricName").asText().isBlank()
          || !List.of("ATOMIC", "DERIVED", "COMPOSITE").contains(node.path("metricType").asText())) {
        throw new IllegalStateException("指标版本快照缺少必要事实");
      }
      var facts = new ArrayList<Fact>();
      int size = 0;
      for (int i = 0; i < KEYS.size(); i++) {
        var value = node.get(KEYS.get(i));
        if (value == null || value.isNull()) continue;
        // Expressions stay whole. Missing legacy facts stay missing instead of reading today's references.
        String text;
        if ("compositions".equals(KEYS.get(i))) {
          if (!value.isArray()) throw new IllegalStateException("指标组合项快照格式无效");
          var items = JSON.createArrayNode();
          for (var item : value) {
            if (!item.isObject()) throw new IllegalStateException("指标组合项快照格式无效");
            var projected = items.addObject();
            for (String key : List.of("subMetricId", "subMetricVersion", "operator", "expression", "sortOrder")) {
              var field = item.get(key);
              if (field != null && !field.isNull()) {
                if (!field.isTextual() && !field.isIntegralNumber()) throw new IllegalStateException("指标组合项事实格式无效");
                projected.set(key, field);
              }
            }
          }
          text = items.toString();
        } else {
          if (!value.isTextual() && !value.isIntegralNumber()) throw new IllegalStateException("指标口径事实格式无效");
          text = value.asText();
        }
        if (text.isBlank()) continue;
        size += text.length();
        if (text.length() > 4096 || size > 16000) throw new IllegalStateException("口径事实超过解释范围，请人工核对原定义");
        facts.add(new Fact(KEYS.get(i), LABELS.get(i), text));
      }
      return new Context(snapshot.getId(), metricId, version, MetricSnapshotDigest.sha256(snapshot.getSnapshot()), List.copyOf(facts));
    } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
      throw new IllegalStateException("指标版本快照无法读取", invalid);
    }
  }
}
