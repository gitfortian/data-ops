package io.yak.ops.business.metric.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metric.api.MetricApi;
import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver;
import io.yak.ops.business.metric.controller.v1.dto.MetricQueryDTO;
import io.yak.ops.business.metric.controller.v1.vo.MetricVO;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.impact.MetricImpactService;
import io.yak.ops.business.metric.repository.MetricCompositionRepository;
import io.yak.ops.business.modeling.api.ModelQueryApi;
import io.yak.ops.common.bean.po.metric.MetricCompositionPO;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 指标管理 REST API。 */
@Tag(name = "指标管理接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metrics")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricController {

  private final MetricCatalogService service;
  private final MetricCompositionRepository compositionRepository;
  private final CurrentUserProvider currentUserProvider;
  private final MetricReferenceResolver referenceResolver;
  private final MetricImpactService impactService;

  @Operation(summary = "创建指标")
  @RequiresPermission(MetricPermissionCode.CREATE)
  @PostMapping
  public Result<MetricVO> create(
      @Valid @RequestBody MetricApi.CreateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    MetricVO vo = MetricVO.from(service.create(request, operator));
    enrichNames(List.of(vo));
    return Result.success(vo);
  }

  @Operation(summary = "指标详情")
  @GetMapping("/{id}")
  public Result<MetricVO> get(@PathVariable("id") Long id) {
    Metric metric = service.get(id);
    MetricVO vo = MetricVO.from(metric);
    List<MetricCompositionPO> compositions = compositionRepository.listByMetric(id);
    if (!compositions.isEmpty()) {
      Map<Long, Metric> subs = referenceResolver.metricsById(compositions.stream()
          .map(MetricCompositionPO::getSubMetricId)
          .filter(subId -> subId != null && subId > 0)
          .collect(Collectors.toSet()));
      vo.setCompositions(compositions.stream()
          .map(po -> toCompositionVO(po, subs)).toList());
    }
    enrichNames(List.of(vo));

    MetricImpactService.ImpactReport dependencyContext = impactService.checkUpstreamChanges(id);
    vo.setDependencyChanges(dependencyContext.changes());
    vo.setAuthoringNextStep(dependencyContext.authoringNextStep());
    return Result.success(vo);
  }

  @Operation(summary = "分页查询指标")
  @PostMapping("/page")
  public Result<PagingData<MetricVO>> page(@Valid @RequestBody MetricQueryDTO query) {
    PageData<Metric> page = service.page(
        query.getPageNo(), query.getPageSize(),
        query.getDomainId(), query.getMetricType(),
        query.getStatus(), query.getKeyword(),
        query.getOwner(), query.getTagIds());
    List<MetricVO> records = page.records().stream()
        .map(MetricVO::from).toList();
    enrichNames(records);
    PageData<MetricVO> voPage = new PageData<>(
        records, page.total(), page.pages(),
        page.pageNo(), page.pageSize());
    return Result.success(PagingData.from(voPage));
  }

  @Operation(summary = "更新指标")
  @RequiresPermission(MetricPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<MetricVO> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody MetricApi.UpdateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    MetricVO vo = MetricVO.from(service.update(id, request, operator));
    enrichNames(List.of(vo));
    return Result.success(vo);
  }

  @Operation(summary = "启用/停用指标")
  @RequiresPermission(MetricPermissionCode.UPDATE)
  @PostMapping("/{id}/status")
  public Result<MetricVO> changeStatus(
      @PathVariable("id") Long id,
      @Valid @RequestBody MetricApi.StatusRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    MetricVO vo = MetricVO.from(service.changeStatus(id, request.status(), operator));
    enrichNames(List.of(vo));
    return Result.success(vo);
  }

  @Operation(summary = "删除指标")
  @RequiresPermission(MetricPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "指标统计(服务端聚合)")
  @GetMapping("/stats")
  public Result<MetricStats> stats() {
    return Result.success(new MetricStats(
        service.count(),
        service.countByType("ATOMIC"),
        service.countByType("DERIVED"),
        service.countByType("COMPOSITE")));
  }

  public record MetricStats(long total, long atomic, long derived, long composite) {}

  private MetricVO.CompositionVO toCompositionVO(MetricCompositionPO po, Map<Long, Metric> subs) {
    MetricVO.CompositionVO vo = new MetricVO.CompositionVO();
    vo.setId(po.getId());
    vo.setSubMetricId(po.getSubMetricId());
    Metric sub = po.getSubMetricId() != null && po.getSubMetricId() > 0
        ? subs.get(po.getSubMetricId()) : null;
    if (sub != null) {
      vo.setSubMetricCode(sub.metricCode());
      vo.setSubMetricName(sub.metricName());
    }
    vo.setOperator(po.getOperator());
    vo.setExpression(po.getExpression());
    vo.setSortOrder(po.getSortOrder() != null ? po.getSortOrder() : 0);
    return vo;
  }

  /** Batch-resolve display names via SPI only (never via foreign dao layers). */
  private void enrichNames(List<MetricVO> vos) {
    if (vos.isEmpty()) {
      return;
    }
    Map<Long, String> domainNames = referenceResolver.domainNames();
    Map<Long, String> processNames = referenceResolver.processNames();
    Map<Long, String> stdLabels = referenceResolver.standardLabels(vos.stream()
        .flatMap(v -> Stream.of(v.getCaliberId(), v.getUnitId()))
        .filter(Objects::nonNull).collect(Collectors.toSet()));
    Map<Long, ModelQueryApi.ModelBrief> models = referenceResolver.modelBriefs(vos.stream()
        .map(MetricVO::getModelId).filter(Objects::nonNull).collect(Collectors.toSet()));
    Map<Long, Metric> refMetrics = referenceResolver.metricsById(vos.stream()
        .map(MetricVO::getRefMetricId).filter(Objects::nonNull).collect(Collectors.toSet()));

    for (MetricVO vo : vos) {
      vo.setDomainName(getOrNull(domainNames, vo.getDomainId()));
      vo.setProcessName(getOrNull(processNames, vo.getProcessId()));
      vo.setCaliberName(getOrNull(stdLabels, vo.getCaliberId()));
      vo.setUnitName(getOrNull(stdLabels, vo.getUnitId()));
      ModelQueryApi.ModelBrief model = getOrNull(models, vo.getModelId());
      if (model != null) {
        vo.setModelName(model.name());
      }
      Metric ref = getOrNull(refMetrics, vo.getRefMetricId());
      if (ref != null) {
        vo.setRefMetricName(ref.metricName());
      }
    }
  }

  /** Immutable Map.of() throws NPE on null keys; keep view assembly null-safe. */
  private static <K, V> V getOrNull(Map<K, V> map, K key) {
    return key == null ? null : map.get(key);
  }
}
