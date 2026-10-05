package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.ops.business.agent.catalog.FieldWhitelistValidator;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.DatasetQuerySpec;
import io.yak.ops.business.agent.domain.DatasetSummary;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.framework.security.service.RoleService;
import io.yak.ops.business.dataset.DatasetQuerySubject;
import io.yak.ops.core.security.ActionAccessDeniedException;
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
 * 证据留痕在执行边界恰好落一条（成功、拒绝与失败都落），失败分类后回喂模型。
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
  private final RoleService roleService;

  public QueryEvidenceView execute(String sessionId, DatasetQuerySpec request,
      DatasetSummary.DatasetFields discovery) {
    DatasetQuerySpec spec = request.withVersion(discovery == null ? null : discovery.versionNo());
    int limit = resolveLimit(spec.limit());
    long startedAt = System.nanoTime();
    try {
      // 执行边界白名单前置：拒绝路径与成功/失败同边界落 query_log（REJECTED），
      // 不依赖工具层提前拦截（工具保持薄壳，留痕归 gateway corridor，DOMAIN 全量留痕契约）。
      whitelistValidator.requireSnapshot(spec.datasetId(), discovery, referencedFields(spec));
      if (!YakSecurityContext.isAuthenticated()) throw new ActionAccessDeniedException("dataset:query");
      String username = YakSecurityContext.getCurrentUsername();
      if (username == null || username.isBlank()) throw new ActionAccessDeniedException("dataset:query");
      List<String> roles = YakSecurityContext.getCurrentRoleIds().stream().distinct().map(id -> {
        var role = roleService.getRoleDetailByRoleId(id);
        if (role == null || !id.equals(role.getId()) || role.getRoleCode() == null || role.getRoleCode().isBlank()) {
          throw new ActionAccessDeniedException("dataset:query");
        }
        return role.getRoleCode();
      }).toList();
      DatasetQueryResultAccess result =
          new DatasetQueryResultAccess(
              queryService.query(
                  spec.datasetId(),
                  new DatasetQueryRequest(
                      spec.versionNo(),
                      spec.dimensions(),
                      toBindings(spec.metrics()),
                      toFilters(spec.filters()),
                      toSorts(spec.sorts()),
                      limit,
                      null), DatasetQuerySubject.authenticatedUser(username, roles)));
      long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
      record(sessionId, spec, QueryEvidenceRecord.Status.SUCCESS, null, result, elapsedMillis);
      return result.toView(elapsedMillis, null);
    } catch (IllegalArgumentException rejection) {
      long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
      String message = String.valueOf(rejection.getMessage());
      boolean rejectedByGuard =
          message.contains("[FIELD_WHITELIST_REJECTED]")
              || message.contains("[DATASET_OFFLINE]")
              || message.contains("[DATASET_DISCOVERY_REQUIRED]")
              || message.contains("[DATASET_VERSION_CHANGED]");
      record(
          sessionId,
          spec,
          rejectedByGuard ? QueryEvidenceRecord.Status.REJECTED : QueryEvidenceRecord.Status.FAILED,
          summarize(rejection),
          null,
          elapsedMillis);
      throw new IllegalArgumentException(rejectedByGuard ? message : "[DATASET_QUERY_FAILED] 查询失败，请到源域查看详情");
    } catch (RuntimeException e) {
      long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
      record(
          sessionId,
          spec,
          e instanceof ActionAccessDeniedException || e instanceof SecurityException
              ? QueryEvidenceRecord.Status.REJECTED : QueryEvidenceRecord.Status.FAILED,
          summarize(e),
          null,
          elapsedMillis);
      throw new IllegalStateException(e instanceof ActionAccessDeniedException || e instanceof SecurityException
          ? "[PERMISSION_DENIED] 无权执行此数据集查询" : "[DATASET_QUERY_FAILED] 查询失败，请到源域查看详情");
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

  private int resolveLimit(Integer requested) {
    int max = Math.max(1, properties.getQuery().getMaxLimit());
    if (requested == null || requested <= 0) {
      return Math.min(properties.getQuery().getDefaultLimit(), max);
    }
    return Math.min(requested, max);
  }

  private static String summarize(RuntimeException e) {
    if (e instanceof ActionAccessDeniedException || e instanceof SecurityException) return "PERMISSION_DENIED";
    String message = e.getMessage();
    if (message != null && message.startsWith("[DATASET_")) return message.split("]", 2)[0] + "]";
    if (message != null && message.startsWith("[FIELD_WHITELIST_REJECTED]")) return "FIELD_WHITELIST_REJECTED";
    return "DATASET_QUERY_FAILED";
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
