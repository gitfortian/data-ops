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
 * <p>Legacy display lookups remain best-effort and degrade to empty values so
 * callers are not blocked. State-aware single-reference lookups preserve the
 * difference between an absent entity and an unavailable/failing provider.
 * Direct imports of other modules' dao layers are forbidden by the module
 * contract (REVIEW #2).
 */
@Component
@Slf4j
public class MetricReferenceResolver {

  /** 上游引用在登记时刻的快照(编码 + 版本号);解析不到时为空项。 */
  public record Reference(String code, Integer version) {
    public static final Reference EMPTY = new Reference(null, null);
  }

  /** Provider/实体解析状态。EMPTY 表示 provider 正常但目标不存在；UNAVAILABLE 表示证据无法取得。 */
  public enum ProviderState {
    AVAILABLE,
    EMPTY,
    UNAVAILABLE
  }

  /** 带可用性证据的引用解析结果，供 canonical detail / validation 使用。 */
  public record ReferenceResolution(Reference reference, ProviderState state) {
    public ReferenceResolution {
      reference = reference == null ? Reference.EMPTY : reference;
      state = state == null ? ProviderState.UNAVAILABLE : state;
    }

    public static ReferenceResolution available(Reference reference) {
      return new ReferenceResolution(reference, ProviderState.AVAILABLE);
    }

    public static ReferenceResolution empty() {
      return new ReferenceResolution(Reference.EMPTY, ProviderState.EMPTY);
    }

    public static ReferenceResolution unavailable() {
      return new ReferenceResolution(Reference.EMPTY, ProviderState.UNAVAILABLE);
    }
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

  /**
   * 口径/单位引用解析，显式保留 provider 可用性。
   * EMPTY = provider 正常但目标不存在；UNAVAILABLE = provider 缺失或调用失败。
   */
  public ReferenceResolution standardReferenceResolution(Long standardId) {
    if (standardId == null || standardId <= 0) {
      return ReferenceResolution.empty();
    }
    try {
      StandardQueryApi api = standardQueryApi.getIfAvailable();
      if (api == null) {
        return ReferenceResolution.unavailable();
      }
      var standard = api.get(standardId);
      return standard == null
          ? ReferenceResolution.empty()
          : ReferenceResolution.available(new Reference(standard.code(), standard.version()));
    } catch (RuntimeException e) {
      log.warn("Reference resolution failed for standard:{} (provider unavailable): {}",
          standardId, e.getMessage());
      return ReferenceResolution.unavailable();
    }
  }

  /** 兼容旧调用方；需要区分 EMPTY/UNAVAILABLE 的新代码应使用 standardReferenceResolution。 */
  public Reference standardReference(Long standardId) {
    return standardReferenceResolution(standardId).reference();
  }

  /**
   * 模型引用解析，显式保留 provider 可用性。
   * EMPTY = provider 正常但目标不存在；UNAVAILABLE = provider 缺失、返回非法结果或调用失败。
   */
  public ReferenceResolution modelReferenceResolution(Long modelId) {
    if (modelId == null || modelId <= 0) {
      return ReferenceResolution.empty();
    }
    try {
      ModelQueryApi api = modelQueryApi.getIfAvailable();
      if (api == null) {
        return ReferenceResolution.unavailable();
      }
      Map<Long, ModelBrief> resolved = api.resolve(List.of(modelId));
      if (resolved == null) {
        return ReferenceResolution.unavailable();
      }
      ModelBrief brief = resolved.get(modelId);
      return brief == null
          ? ReferenceResolution.empty()
          : ReferenceResolution.available(new Reference(brief.code(), brief.latestVersionNo()));
    } catch (RuntimeException e) {
      log.warn("Reference resolution failed for model:{} (provider unavailable): {}",
          modelId, e.getMessage());
      return ReferenceResolution.unavailable();
    }
  }

  /** 兼容旧调用方；需要区分 EMPTY/UNAVAILABLE 的新代码应使用 modelReferenceResolution。 */
  public Reference modelReference(Long modelId) {
    return modelReferenceResolution(modelId).reference();
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
