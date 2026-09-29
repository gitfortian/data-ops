package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.ops.business.agent.catalog.FieldWhitelistValidator;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.DatasetQuerySpec;
import io.yak.ops.business.agent.domain.QueryEvidenceRecord;
import io.yak.ops.business.agent.domain.QueryEvidenceView;
import io.yak.ops.business.agent.repository.QueryLogRepository;
import io.yak.ops.business.agent.repository.support.QueryRequestJsonCodec;
import io.yak.ops.business.dataset.DatasetAggregation;
import io.yak.ops.business.dataset.DatasetFilter;
import io.yak.ops.business.dataset.DatasetFilterOperator;
import io.yak.ops.business.dataset.DatasetMetricBinding;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetQueryService;
import io.yak.ops.business.dataset.DatasetSort;
import io.yak.ops.business.dataset.DatasetSortDirection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据集查询出站网关：结构化规格 -> dataset 查询运行时。
 * 证据留痕在执行边界恰好落一条（成功与失败都落），失败原样上抛由工具层回喂模型。
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class DatasetQueryGateway {

  private final DatasetQueryService queryService;
  private final FieldWhitelistValidator whitelistValidator;
  private final QueryLogRepository queryLogRepository;
  private final AgentProperties properties;

  public QueryEvidenceView execute(String sessionId, DatasetQuerySpec spec) {
    int limit = resolveLimit(spec.limit());
    long startedAt = System.nanoTime();
    try {
      // 执行边界白名单前置：拒绝路径与成功/失败同边界落 query_log（REJECTED），
      // 不依赖工具层提前拦截（工具保持薄壳，留痕归 gateway corridor，DOMAIN 全量留痕契约）。
      whitelistValidator.requireKnownFields(
          spec.datasetId(), referencedFields(spec), spec.datasetId() + " 字段引用");
      DatasetQueryResultAccess result =
          new DatasetQueryResultAccess(
              queryService.query(
                  spec.datasetId(),
                  new DatasetQueryRequest(
                      null,
                      spec.dimensions(),
                      toBindings(spec.metrics()),
                      toFilters(spec.filters()),
                      toSorts(spec.sorts()),
                      limit,
                      null)));
      long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
      String sql = resolveSql(spec.datasetId(), result.queryId());
      record(sessionId, spec, QueryEvidenceRecord.Status.SUCCESS, null, result, elapsedMillis);
      return result.toView(elapsedMillis, sql);
    } catch (IllegalArgumentException rejection) {
      long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
      String message = String.valueOf(rejection.getMessage());
      boolean rejectedByGuard =
          message.contains("[FIELD_WHITELIST_REJECTED]")
              || message.contains("[DATASET_OFFLINE]");
      record(
          sessionId,
          spec,
          rejectedByGuard ? QueryEvidenceRecord.Status.REJECTED : QueryEvidenceRecord.Status.FAILED,
          summarize(rejection),
          null,
          elapsedMillis);
      throw rejection;
    } catch (RuntimeException e) {
      long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
      record(
          sessionId,
          spec,
          QueryEvidenceRecord.Status.FAILED,
          summarize(e),
          null,
          elapsedMillis);
      throw e;
    }
  }

  /** 从规格中收集全部字段引用（白名单校验输入）。 */
  private static List<String> referencedFields(DatasetQuerySpec spec) {
    List<String> referenced = new java.util.ArrayList<>();
    if (spec.dimensions() != null) {
      referenced.addAll(spec.dimensions());
    }
    if (spec.metrics() != null) {
      spec.metrics().forEach(metric -> referenced.add(metric.fieldId()));
    }
    if (spec.filters() != null) {
      spec.filters().forEach(filter -> referenced.add(filter.fieldId()));
    }
    if (spec.sorts() != null) {
      spec.sorts().forEach(sort -> referenced.add(sort.fieldId()));
    }
    return referenced;
  }

  private void record(
      String sessionId,
      DatasetQuerySpec spec,
      QueryEvidenceRecord.Status status,
      String errorMessage,
      DatasetQueryResultAccess result,
      long elapsedMillis) {
    queryLogRepository.record(
        new QueryEvidenceRecord(
            sessionId,
            spec.datasetId(),
            result == null ? null : result.queryId(),
            QueryRequestJsonCodec.write(spec),
            status,
            errorMessage,
            result == null ? null : result.returnedRows(),
            result == null ? null : result.truncated(),
            elapsedMillis));
  }

  /** 从查询性能留痕中取回实际执行的 SQL（读取失败不影响主流程，返回 null）。 */
  private String resolveSql(long datasetId, String queryId) {
    if (queryId == null) {
      return null;
    }
    try {
      return queryService
          .recentPerformance(java.util.Set.of(datasetId), java.util.Set.of(queryId), 1)
          .stream()
          .filter(perf -> queryId.equals(perf.queryId()))
          .map(io.yak.ops.business.dataset.DatasetQueryPerformance::sql)
          .findFirst()
          .orElse(null);
    } catch (RuntimeException e) {
      log.debug("resolve executed sql failed: {}", e.getMessage());
      return null;
    }
  }

  private int resolveLimit(Integer requested) {
    int max = Math.max(1, properties.getQuery().getMaxLimit());
    if (requested == null || requested <= 0) {
      return Math.min(properties.getQuery().getDefaultLimit(), max);
    }
    return Math.min(requested, max);
  }

  private static String summarize(RuntimeException e) {
    String message = e.getMessage();
    if (message == null) {
      return e.getClass().getSimpleName();
    }
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }

  private static List<DatasetMetricBinding> toBindings(List<DatasetQuerySpec.Metric> metrics) {
    if (metrics == null) {
      return List.of();
    }
    return metrics.stream()
        .map(metric -> new DatasetMetricBinding(metric.fieldId(), DatasetAggregation.valueOf(metric.aggregation().name())))
        .toList();
  }

  private static List<DatasetFilter> toFilters(List<DatasetQuerySpec.Filter> filters) {
    if (filters == null) {
      return List.of();
    }
    return filters.stream()
        .map(
            filter ->
                new DatasetFilter(
                    filter.fieldId(),
                    DatasetFilterOperator.valueOf(filter.operator().name()),
                    filter.value(),
                    toObjectValues(filter.values())))
        .toList();
  }

  private static List<Object> toObjectValues(List<String> values) {
    if (values == null) {
      return null;
    }
    return new java.util.ArrayList<>(values);
  }

  private static List<DatasetSort> toSorts(List<DatasetQuerySpec.Sort> sorts) {
    if (sorts == null) {
      return List.of();
    }
    return sorts.stream()
        .map(
            sort ->
                new DatasetSort(
                    sort.fieldId(),
                    null,
                    DatasetSortDirection.valueOf(sort.direction().name())))
        .toList();
  }

  /** 隔离 dataset 返回类型的读取壳，避免类型泄漏到网关签名。 */
  private record DatasetQueryResultAccess(io.yak.ops.business.dataset.DatasetQueryResult result) {

    String queryId() {
      return result.queryId();
    }

    int returnedRows() {
      return result.returnedRows();
    }

    boolean truncated() {
      return result.truncated();
    }

    QueryEvidenceView toView(long elapsedMillis, String sql) {
      List<String> columns =
          result.columns().stream().map(column -> column.name()).toList();
      return new QueryEvidenceView(
          result.queryId(),
          columns,
          result.rows(),
          result.returnedRows(),
          result.truncated(),
          elapsedMillis,
          sql);
    }
  }
}
