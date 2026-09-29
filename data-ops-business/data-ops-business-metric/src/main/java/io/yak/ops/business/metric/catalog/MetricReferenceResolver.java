package io.yak.ops.business.metric.catalog;

import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.modeling.api.ModelQueryApi;
import io.yak.ops.business.modeling.api.ModelQueryApi.ModelBrief;
import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.StandardKind;
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
 * <p>Legacy display lookups remain best-effort. Product-facing dependency resolution additionally
 * preserves whether a reference is confirmed missing or could not be resolved because its provider
 * is unavailable, so REMOVED and UNAVAILABLE never collapse into the same null value.
 */
@Component
@Slf4j
public class MetricReferenceResolver {

  /** 上游引用在登记时刻的快照(编码 + 版本号);解析不到时为空项。 */
  public record Reference(String code, Integer version) {
    public static final Reference EMPTY = new Reference(null, null);
  }

  public enum ResolutionStatus {
    READY,
    REMOVED,
    UNAVAILABLE,
    FORBIDDEN
  }

  public record ReferenceResolution(Reference reference, ResolutionStatus status) {
    public static ReferenceResolution ready(Reference reference) {
      return new ReferenceResolution(reference, ResolutionStatus.READY);
    }

    public static ReferenceResolution removed() {
      return new ReferenceResolution(Reference.EMPTY, ResolutionStatus.REMOVED);
    }

    public static ReferenceResolution unavailable() {
      return new ReferenceResolution(Reference.EMPTY, ResolutionStatus.UNAVAILABLE);
    }

    public static ReferenceResolution forbidden() {
      return new ReferenceResolution(Reference.EMPTY, ResolutionStatus.FORBIDDEN);
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

  /** Legacy snapshot lookup retained for existing callers. */
  public Reference standardReference(Long standardId) {
    return standardReferenceResolution(standardId).reference();
  }

  /**
   * Product-facing standard dependency lookup.
   * A normal null response means the referenced object is confirmed removed; missing provider or
   * provider failure means UNAVAILABLE and must not be rendered as removed.
   */
  public ReferenceResolution standardReferenceResolution(Long standardId) {
    if (standardId == null || standardId <= 0) {
      return ReferenceResolution.unavailable();
    }
    StandardQueryApi api = standardQueryApi.getIfAvailable();
    if (api == null) {
      return ReferenceResolution.unavailable();
    }
    try {
      var standard = api.get(standardId);
      if (standard == null) {
        return ReferenceResolution.removed();
      }
      return ReferenceResolution.ready(new Reference(standard.code(), standard.version()));
    } catch (SecurityException e) {
      return ReferenceResolution.forbidden();
    } catch (RuntimeException e) {
      log.warn("Reference resolution failed for standard:{} (unavailable): {}", standardId, e.getMessage());
      return ReferenceResolution.unavailable();
    }
  }

  public ReferenceResolution standardReferenceResolution(Long standardId, StandardKind expectedKind) {
    if (standardId == null || standardId <= 0) return ReferenceResolution.removed();
    StandardQueryApi api = standardQueryApi.getIfAvailable();
    if (api == null) return ReferenceResolution.unavailable();
    try {
      var standard = api.get(standardId);
      if (standard == null || standard.kind() != expectedKind) return ReferenceResolution.removed();
      return ReferenceResolution.ready(new Reference(standard.code(), standard.version()));
    } catch (SecurityException e) {
      return ReferenceResolution.forbidden();
    } catch (RuntimeException e) {
      log.warn("Reference resolution failed for standard:{} (unavailable): {}", standardId, e.getMessage());
      return ReferenceResolution.unavailable();
    }
  }

  public ReferenceResolution domainResolution(Long domainId) {
    if (domainId == null || domainId <= 0) return ReferenceResolution.removed();
    ProcessApi api = processApi.getIfAvailable();
    if (api == null) return ReferenceResolution.unavailable();
    try {
      return api.listDomains().stream().filter(domain -> domainId.equals(domain.id())).findFirst()
          .map(domain -> ReferenceResolution.ready(new Reference(domain.code(), null)))
          .orElseGet(ReferenceResolution::removed);
    } catch (SecurityException e) {
      return ReferenceResolution.forbidden();
    } catch (RuntimeException e) {
      log.warn("Reference resolution failed for domain:{} (unavailable): {}", domainId, e.getMessage());
      return ReferenceResolution.unavailable();
    }
  }

  public ReferenceResolution processResolution(Long processId, Long domainId) {
    if (processId == null || processId <= 0) return ReferenceResolution.removed();
    ProcessApi api = processApi.getIfAvailable();
    if (api == null) return ReferenceResolution.unavailable();
    try {
      return api.listProcesses(domainId).stream()
          .filter(process -> processId.equals(process.id())
              && (domainId == null || domainId.equals(process.domainId())))
          .findFirst()
          .map(process -> ReferenceResolution.ready(new Reference(process.code(), null)))
          .orElseGet(ReferenceResolution::removed);
    } catch (SecurityException e) {
      return ReferenceResolution.forbidden();
    } catch (RuntimeException e) {
      log.warn("Reference resolution failed for process:{} (unavailable): {}", processId, e.getMessage());
      return ReferenceResolution.unavailable();
    }
  }

  /** Legacy snapshot lookup retained for existing callers. */
  public Reference modelReference(Long modelId) {
    return modelReferenceResolution(modelId).reference();
  }

  /** Product-facing model dependency lookup preserving REMOVED vs UNAVAILABLE. */
  public ReferenceResolution modelReferenceResolution(Long modelId) {
    if (modelId == null || modelId <= 0) {
      return ReferenceResolution.unavailable();
    }
    ModelQueryApi api = modelQueryApi.getIfAvailable();
    if (api == null) {
      return ReferenceResolution.unavailable();
    }
    try {
      ModelBrief brief = api.resolve(List.of(modelId)).get(modelId);
      if (brief == null) {
        return ReferenceResolution.removed();
      }
      return ReferenceResolution.ready(new Reference(brief.code(), brief.latestVersionNo()));
    } catch (SecurityException e) {
      return ReferenceResolution.forbidden();
    } catch (RuntimeException e) {
      log.warn("Reference resolution failed for model:{} (unavailable): {}", modelId, e.getMessage());
      return ReferenceResolution.unavailable();
    }
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
