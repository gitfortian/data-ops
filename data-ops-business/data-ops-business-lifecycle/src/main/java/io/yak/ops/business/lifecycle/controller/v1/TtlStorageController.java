package io.yak.ops.business.lifecycle.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.lifecycle.stats.StorageStatsService;
import io.yak.ops.business.lifecycle.stats.StorageStatsService.StorageStats;
import io.yak.ops.business.lifecycle.stats.StorageStatsService.TableStorage;
import io.yak.ops.business.lifecycle.stats.StorageStatsService.TrendPoint;
import io.yak.ops.common.constant.lifecycle.LifecyclePermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 存储统计 REST API(ticket 87 配套:量/热冷/趋势/成本)。 */
@Tag(name = "数据生命周期-存储统计")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(LifecyclePermissionCode.READ)
public class TtlStorageController {

  @Data
  public static class PriceDTO {
    @NotBlank(message = "单价不能为空")
    private String pricePerGbMonth;
  }

  private final StorageStatsService statsService;

  @Operation(summary = "存储统计(最新快照:各层量+热冷分布+成本)")
  @GetMapping("/storage/stats")
  public Result<StorageStats> stats() {
    return Result.success(statsService.stats());
  }

  @Operation(summary = "存储趋势(近 N 天)")
  @GetMapping("/storage/trend")
  public Result<List<TrendPoint>> trend(@RequestParam(value = "days", defaultValue = "30") int days) {
    return Result.success(statsService.trend(days));
  }

  /**
   * 单表最近一次快照(元数据详情页的存储块)。
   *
   * <p>{@code databaseName} 必填:同一数据源多库时只按表名查会命中别人的库(T16)。
   * 没有快照时 {@code data} 为 null,调用方显示"无快照"而不是 0 字节。
   */
  @Operation(summary = "单表存储快照(近似值,按库名过滤)")
  @GetMapping("/storage/table")
  public Result<TableStorage> tableStorage(
      @RequestParam(value = "datasourceId", required = false) Long datasourceId,
      @RequestParam("databaseName") String databaseName,
      @RequestParam("tableName") String tableName) {
    Optional<TableStorage> snapshot =
        statsService.latestTableStorage(datasourceId, databaseName, tableName);
    return Result.success(snapshot.orElse(null));
  }

  @Operation(summary = "查询存储单价设置")
  @GetMapping("/storage/setting")
  public Result<PriceDTO> getSetting() {
    PriceDTO dto = new PriceDTO();
    dto.setPricePerGbMonth(statsService.getSetting(StorageStatsService.PRICE_KEY));
    return Result.success(dto);
  }

  @Operation(summary = "保存存储单价设置")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PutMapping("/storage/setting")
  public Result<Boolean> putSetting(@Valid @RequestBody PriceDTO dto) {
    statsService.putSetting(StorageStatsService.PRICE_KEY, dto.getPricePerGbMonth().trim());
    return Result.success(Boolean.TRUE);
  }
}
