package io.yak.ops.business.asset.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.asset.approval.AssetPublishApprovalService;
import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetAppService.AssetView;
import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.business.asset.application.AssetDiscoverService;
import io.yak.ops.business.asset.application.AssetDiscoverService.AssetDetailView;
import io.yak.ops.business.asset.application.AssetLineageSummaryService;
import io.yak.ops.business.asset.application.AssetLineageSummaryService.LineageSummary;
import io.yak.ops.business.asset.application.AssetLifecycleService;
import io.yak.ops.business.asset.application.AssetLifecycleService.PrecheckResult;
import io.yak.ops.business.asset.application.AssetOverviewService;
import io.yak.ops.business.asset.application.AssetViewRecordService;
import io.yak.ops.business.asset.catalog.DirectoryService;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.BatchMoveAssetsDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.IgnoreAssetsDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ItemEditDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ItemQueryDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ManualRegisterDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.OfflineDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.OwnerDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.PrecheckDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.PublishDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ViewReportDTO;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 资产台账 REST API(ticket 91;搜索/360° 详情在 ticket 97 扩展)。 */
@Tag(name = "数据资产-台账")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/assets")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.READ)
public class AssetController {

  private final AssetAppService assetService;
  private final AssetLifecycleService lifecycleService;
  private final AssetPublishApprovalService publishApprovalService;
  private final AssetDiscoverService discoverService;
  private final AssetLineageSummaryService lineageSummaryService;
  private final AssetOverviewService overviewService;
  private final AssetViewRecordService viewRecordService;
  private final DirectoryService directoryService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "治理驾驶舱:KPI+分布+待办+最近动态(固定 ≤8 查询,ticket 98)")
  @GetMapping("/overview")
  public Result<Map<String, Object>> overview() {
    return Result.success(overviewService.overview());
  }

  @Operation(summary = "台账分页/搜索(多选筛选+健康×活跃度默认排序,ticket 97)")
  @GetMapping
  public Result<PagingData<AssetView>> page(@Valid ItemQueryDTO query) {
    PageData<AssetView> page = assetService.page(query);
    return Result.success(PagingData.from(page));
  }

  @Operation(summary = "360° 详情(本体+分区容错聚合,design §6.4)")
  @GetMapping("/{id}")
  public Result<AssetDetailView> detail(
      @PathVariable("id") Long id, HttpServletRequest httpRequest) {
    return Result.success(discoverService.detail(
        id, currentUserProvider.getCurrentUser(httpRequest)));
  }

  @Operation(summary = "按分区独立读取治理证据(五态、来源失败隔离)")
  @GetMapping("/{id}/sections/{sectionType}")
  public Result<AssetSectionResult> section(
      @PathVariable("id") Long id,
      @PathVariable("sectionType") String sectionType,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    AssetDiscoverService.SectionView view = discoverService.section(id, sectionType, operator);
    SectionType type = SectionType.valueOf(sectionType.trim().toUpperCase());
    AssetView asset = assetService.get(id);
    if (view.data() instanceof io.yak.ops.spi.section.SectionContract contract) {
      Map<String, Object> values = contract.summary() == null
          ? Map.of() : contract.summary().values();
      return Result.success(new AssetSectionResult(
          contract.sectionType(), contract.status(), contract.ownerDomain(),
          new SectionMapSummary(values), contract.reason(), contract.updatedAt(),
          contract.actions(), contract.evidence(), contract.provenance(), contract.capability()));
    }
    return Result.success(toSectionResult(type, view, asset));
  }

  static AssetSectionResult toSectionResult(
      SectionType type, AssetDiscoverService.SectionView view, AssetView asset) {
    SectionStatus status = SectionStatus.valueOf(view.status());
    String owner = switch (type) {
      case OVERVIEW, GOVERNANCE -> "ASSET";
      case USAGE -> "FEDERATED";
      case TECHNICAL_METADATA -> "METADATA";
      case QUALITY -> "QUALITY";
      case SECURITY -> "SECURITY";
      case LINEAGE -> "LINEAGE";
      case LIFECYCLE -> "LIFECYCLE";
    };
    Map<String, Object> values = new LinkedHashMap<>();
    if (view.data() instanceof Map<?, ?> map) {
      map.forEach((key, value) -> values.put(String.valueOf(key), value));
    } else if (view.data() != null) {
      values.put("data", view.data());
    }
    boolean hasReadableResult = status == SectionStatus.OK || status == SectionStatus.EMPTY;
    AssetSectionResult result = new AssetSectionResult(
        type, status, owner, new SectionMapSummary(values),
        status == SectionStatus.OK ? null : view.note(),
        null, sectionActions(type, status, asset), List.of(),
        hasReadableResult ? new SectionProvenance(owner, asset.assetKey(), Instant.now()) : null,
        new SectionCapability(status != SectionStatus.NOT_APPLICABLE,
            status == SectionStatus.OK || status == SectionStatus.EMPTY,
            status == SectionStatus.OK || status == SectionStatus.EMPTY ? null : view.note()));
    return result;
  }

  private static List<SectionAction> sectionActions(
      SectionType type, SectionStatus status, AssetView asset) {
    if (status != SectionStatus.OK) {
      return List.of();
    }
    String returnContext = "returnAssetId=" + asset.id();
    return switch (type) {
      case TECHNICAL_METADATA -> List.of(new SectionAction(
          "查看元数据工作台",
          "/data-asset/catalog?view=entity&" + returnContext,
          asset.assetKey()));
      case LINEAGE -> List.of(new SectionAction(
          "打开全屏血缘图谱",
          "/data-analysis/lineage?assetKey=" + java.net.URLEncoder.encode(
              asset.assetKey(), java.nio.charset.StandardCharsets.UTF_8)
              + "&" + returnContext,
          asset.assetKey()));
      default -> List.of();
    };
  }

  @Operation(summary = "跨域引用摘要:上游 N 张表/其中 M 张未稽核(M2-3,只读,供指标详情等消费侧)")
  @GetMapping("/lineage-summary")
  public Result<LineageSummary> lineageSummary(
      @RequestParam("assetKey") @NotBlank @Size(max = 512) String assetKey,
      @RequestParam(value = "depth", defaultValue = "3") @Min(1) @Max(10) int depth) {
    return Result.success(lineageSummaryService.summarize(assetKey, depth));
  }

  @Operation(summary = "浏览上报(同用户同资产 5 分钟去重)")
  @PostMapping("/{id}/view")
  public Result<Boolean> reportView(
      @PathVariable("id") Long id,
      @RequestBody(required = false) ViewReportDTO dto,
      HttpServletRequest httpRequest) {
    String viewer = currentUserProvider.getCurrentUser(httpRequest);
    assetService.get(id);
    return Result.success(viewRecordService.record(id, viewer,
        dto == null ? "detail" : dto.getEntry()));
  }

  @Operation(summary = "浏览趋势(近 30 天按日计数)")
  @GetMapping("/{id}/views")
  public Result<List<AssetViewRecordService.DailyView>> views(@PathVariable("id") Long id) {
    assetService.get(id);
    return Result.success(viewRecordService.trend(id, 30));
  }

  @Operation(summary = "手工登记(仅 MANUAL)")
  @RequiresPermission(AssetPermissionCode.CREATE)
  @PostMapping
  public Result<AssetView> registerManual(
      @Valid @RequestBody ManualRegisterDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(assetService.registerManual(dto, operator));
  }

  @Operation(summary = "编辑快照字段(名称/描述/访问入口)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<AssetView> updateSnapshot(
      @PathVariable("id") Long id,
      @Valid @RequestBody ItemEditDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        assetService.updateSnapshot(id, dto.getName(), dto.getDescription(), dto.getAccessUri(),
            operator));
  }

  @Operation(summary = "变更统一负责人")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PutMapping("/{id}/owner")
  public Result<AssetView> changeOwner(
      @PathVariable("id") Long id,
      @Valid @RequestBody OwnerDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(assetService.changeOwner(id, dto.getOwner(), operator));
  }

  @Operation(summary = "上架预检:缺口清单(负责人/描述/目录)+ 5 分钟 token")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/precheck")
  public Result<PrecheckResult> precheck(
      @Valid @RequestBody PrecheckDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(lifecycleService.precheck(dto.getAssetIds(), operator));
  }

  @Operation(summary = "上架(token 必带 48005;有缺口须 acceptRisk 48004)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/publish")
  public Result<Integer> publish(
      @Valid @RequestBody PublishDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        lifecycleService.publish(dto.getAssetIds(), dto.getToken(), dto.isAcceptRisk(), operator));
  }

  @Operation(summary = "发起上架审批(M2-5;同资产在途单唯一 49003,批准即走 publish 链路)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/{id}/publish-approval")
  public Result<ApprovalInstanceView> submitPublishApproval(
      @PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(publishApprovalService.submit(id, operator));
  }

  @Operation(summary = "下架(原因必填 48013,仅已上架)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/offline")
  public Result<Integer> offline(
      @Valid @RequestBody OfflineDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(lifecycleService.offline(dto.getAssetIds(), dto.getReason(), operator));
  }

  @Operation(summary = "忽略(对账抑制态;仅待上架/已下架,48016)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/ignore")
  public Result<Integer> ignore(
      @Valid @RequestBody IgnoreAssetsDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(lifecycleService.ignore(dto.getAssetIds(), operator));
  }

  @Operation(summary = "批量移目录(directoryId 空=移出)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/batch/move-directory")
  public Result<Integer> batchMoveDirectory(
      @Valid @RequestBody BatchMoveAssetsDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(directoryService.moveAssets(dto.getAssetIds(), dto.getDirectoryId(),
        operator));
  }

  @Operation(summary = "删除台账(软删,仅已下架/源已消失)")
  @RequiresPermission(AssetPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    assetService.delete(id, operator);
    return Result.success(Boolean.TRUE);
  }
}
