package io.yak.ops.business.dataset.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dataset.DatasetQueryService;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.business.dataset.DatasetService;
import io.yak.ops.business.dataset.controller.v1.converter.DatasetRequestConverter;
import io.yak.ops.business.dataset.controller.v1.converter.DatasetViewConverter;
import io.yak.ops.business.dataset.controller.v1.dto.DatasetRequests.CreateDatasetVersionRequest;
import io.yak.ops.business.dataset.controller.v1.dto.DatasetRequests.PublishDatasetRequest;
import io.yak.ops.business.dataset.controller.v1.dto.DatasetRequests.QueryDatasetRequest;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetCatalogVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetDetailVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetQueryPerformanceVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetQueryResultVO;
import io.yak.ops.business.dataset.controller.v1.vo.DatasetViews.DatasetVO;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dataset HTTP adapter. Business rules stay in application services. */
@Tag(name = "BI 数据集接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/datasets")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class DatasetController {

  private final DatasetService datasetService;
  private final DatasetQueryService queryService;
  private final DatasetRequestConverter requestConverter;
  private final DatasetViewConverter viewConverter;

  @Operation(summary = "查询 Dataset 列表")
  @GetMapping
  public Result<List<DatasetVO>> list() {
    return Result.success(datasetService.list().stream().map(viewConverter::dataset).toList());
  }

  @Operation(summary = "批量查询 Dataset 当前版本与字段目录")
  @GetMapping("/catalog")
  public Result<List<DatasetCatalogVO>> catalog(
      @RequestParam(value = "datasetIds", required = false) List<Long> datasetIds,
      @RequestParam(value = "onlineOnly", defaultValue = "false") boolean onlineOnly) {
    return Result.success(
        datasetService.catalog(datasetIds, onlineOnly).stream().map(viewConverter::catalog).toList());
  }

  @Operation(summary = "查询 Dataset SQL 运行诊断记录")
  @GetMapping("/query-performance")
  public Result<List<DatasetQueryPerformanceVO>> queryPerformance(
      @RequestParam(value = "datasetIds", required = false) List<Long> datasetIds,
      @RequestParam(value = "queryIds", required = false) List<String> queryIds,
      @RequestParam(value = "statuses", required = false) List<DatasetQueryStatus> statuses,
      @RequestParam(value = "minTotalMillis", required = false) Long minTotalMillis,
      @RequestParam(value = "limit", defaultValue = "100") int limit) {
    Set<Long> datasetFilters = datasetIds == null ? Set.of() : new HashSet<>(datasetIds);
    Set<String> queryFilters = queryIds == null ? Set.of() : new HashSet<>(queryIds);
    Set<DatasetQueryStatus> statusFilters = statuses == null ? Set.of() : new HashSet<>(statuses);
    return Result.success(queryService.recentPerformance(
            datasetFilters, queryFilters, statusFilters, minTotalMillis, limit).stream()
        .map(viewConverter::performance)
        .toList());
  }

  @Operation(summary = "查询 Dataset 详情、当前版本与版本历史")
  @GetMapping("/{datasetId}")
  public Result<DatasetDetailVO> get(@PathVariable("datasetId") long datasetId) {
    return Result.success(viewConverter.detail(datasetService.get(datasetId)));
  }

  @Operation(summary = "把 ONLINE SQL TaskAsset 的当前不可变版本发布为 Dataset")
  @PostMapping
  public Result<DatasetDetailVO> publish(@Valid @RequestBody PublishDatasetRequest request) {
    return Result.success(viewConverter.detail(datasetService.publish(requestConverter.publish(request))));
  }

  @Operation(summary = "把来源 TaskAsset 的当前版本发布为新的 DatasetVersion")
  @PostMapping("/{datasetId}/versions")
  public Result<DatasetDetailVO> createVersion(
      @PathVariable("datasetId") long datasetId,
      @Valid @RequestBody(required = false) CreateDatasetVersionRequest request) {
    return Result.success(viewConverter.detail(datasetService.createVersion(
        datasetId,
        request == null ? List.of() : requestConverter.fields(request.fields()))));
  }

  @Operation(summary = "通过 Dataset Query Runtime 查询当前或指定不可变版本")
  @PostMapping("/{datasetId}/query")
  public Result<DatasetQueryResultVO> query(
      @PathVariable("datasetId") long datasetId,
      @Valid @RequestBody(required = false) QueryDatasetRequest request) {
    return Result.success(viewConverter.queryResult(
        queryService.query(datasetId, requestConverter.query(request))));
  }

  @Operation(summary = "上线 Dataset")
  @PostMapping("/{datasetId}/online")
  public Result<DatasetDetailVO> online(@PathVariable("datasetId") long datasetId) {
    return Result.success(viewConverter.detail(datasetService.online(datasetId)));
  }

  @Operation(summary = "下线 Dataset")
  @PostMapping("/{datasetId}/offline")
  public Result<DatasetDetailVO> offline(@PathVariable("datasetId") long datasetId) {
    return Result.success(viewConverter.detail(datasetService.offline(datasetId)));
  }
}
