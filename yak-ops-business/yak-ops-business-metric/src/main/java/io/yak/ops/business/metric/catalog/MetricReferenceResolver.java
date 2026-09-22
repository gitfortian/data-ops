package io.yak.ops.business.metric.catalog;

import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.modeling.api.ModelQueryApi;
import io.yak.ops.business.modeling.api.ModelQueryApi.ModelBrief;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.business.semantic.api.BusinessProcess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Resolves cross-module references (domain/process/caliber/unit/model) and
 * in-module display names through SPI interfaces only.
 *
 * <p>All lookups are best-effort: an unavailable or failing provider degrades
 * to an empty result and never blocks the caller. Direct imports of other
 * modules' dao layers are forbidden by the module contract (REVIEW #2).
 */
@Component
@Slf4j
public class MetricReferenceResolver {

  /** 上游引用在登记时刻的快照(编码 + 版本号);解析不到时为空项。 */
  public record Reference(String code, Integer version) {
    public static final Reference EMPTY = new Reference(null, null);
  }

  private final MetricRepository repository;
  private final ObjectProvider<ProcessApi> processApi;
  private final ObjectProvider<StandardQueryApi> standardQueryApi;
  private final ObjectProvider<ModelQueryApi> modelQueryApi;

  public MetricReferenceResolver(
      MetricRepository repository,
      ObjectProvider<ProcessApi> processApi,
      ObjectProvider<StandardQueryApi> standardQueryApi,
      ObjectProvider<ModelQueryApi> modelQueryApi) {
    this.repository = repository;
    this.processApi = processApi;
    this.standardQueryApi = standardQueryApi;
    this.modelQueryApi = modelQueryApi;
  }

  /** 业务域展示名:含层级的完整路径(如 交易域/订单),避免与业务过程混淆(M-6)。 */
  public Map<Long, String> domainNames() {
    return safeValue("domains", () -> {
      List<BusinessDomain> domains = processApi.getIfAvailable().listDomains();
      Map<Long, BusinessDomain> byId = domains.stream()
          .collect(Collectors.toMap(BusinessDomain::id, Function.identity(), (a, b) -> a));
      Map<Long, String> result = new LinkedHashMap<>();
      domains.forEach(domain -> result.put(domain.id(), domainPath(domain, byId)));
      return result;
    }, Map.of());
  }

  private static String domainPath(BusinessDomain domain, Map<Long, BusinessDomain> byId) {
    List<String> parts = new ArrayList<>();
    Set<Long> guard = new HashSet<>();
    BusinessDomain current = domain;
    while (current != null && guard.add(current.id())) {
      parts.add(0, current.name());
      Long parentId = current.parentId();
      current = parentId == null || parentId == BusinessDomain.ROOT_PARENT_ID
          ? null
          : byId.get(parentId);
    }
    return String.join("/", parts);
  }

  public Map<Long, String> processNames() {
    return safeValue("processes", () -> processApi.getIfAvailable().listProcesses(null).stream()
        .collect(Collectors.toMap(BusinessProcess::id, BusinessProcess::name, (a, b) -> a)), Map.of());
  }

  /** 口径/单位等标准的展示标签(id → 「名称（编码）」)。 */
  public Map<Long, String> standardLabels(Collection<Long> ids) {
    Collection<Long> distinct = distinctIds(ids);
    if (distinct.isEmpty()) {
      return Map.of();
    }
    return safeValue("standards", () -> standardQueryApi.getIfAvailable().labels(distinct), Map.of());
  }

  public Map<Long, ModelBrief> modelBriefs(Collection<Long> ids) {
    Collection<Long> distinct = distinctIds(ids);
    if (distinct.isEmpty()) {
      return Map.of();
    }
    return safeValue("models", () -> modelQueryApi.getIfAvailable().resolve(distinct), Map.of());
  }

  public Map<Long, Metric> metricsById(Collection<Long> ids) {
    List<Long> distinct = List.copyOf(distinctIds(ids));
    if (distinct.isEmpty()) {
      return Map.of();
    }
    return repository.listByIds(distinct).stream()
        .collect(Collectors.toMap(Metric::id, Function.identity(), (a, b) -> a));
  }

  /** 口径/单位引用的 code+version 快照(写路径单条解析,best-effort)。 */
  public Reference standardReference(Long standardId) {
    if (standardId == null || standardId <= 0) {
      return Reference.EMPTY;
    }
    return safeValue("standard:" + standardId,
        () -> {
          var standard = standardQueryApi.getIfAvailable().get(standardId);
          return standard == null
              ? Reference.EMPTY
              : new Reference(standard.code(), standard.version());
        },
        Reference.EMPTY);
  }

  /** 模型引用的 code+version 快照(写路径单条解析,best-effort)。 */
  public Reference modelReference(Long modelId) {
    if (modelId == null || modelId <= 0) {
      return Reference.EMPTY;
    }
    return safeValue("model:" + modelId,
        () -> {
          var brief = modelQueryApi.getIfAvailable().resolve(List.of(modelId)).get(modelId);
          return brief == null ? Reference.EMPTY : new Reference(brief.code(), brief.latestVersionNo());
        },
        Reference.EMPTY);
  }

  private static Collection<Long> distinctIds(Collection<Long> ids) {
    if (ids == null) {
      return List.of();
    }
    return ids.stream().filter(java.util.Objects::nonNull).filter(id -> id > 0).distinct().toList();
  }

  private static <T> T safeValue(String what, Supplier<T> supplier, T fallback) {
    try {
      T value = supplier.get();
      return value != null ? value : fallback;
    } catch (RuntimeException e) {
      log.warn("Reference resolution failed for {} (degraded to fallback): {}", what, e.getMessage());
      return fallback;
    }
  }
}
