package io.yak.ops.business.metadata.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.detail.EntityDetailService;
import io.yak.ops.business.metadata.detail.EntityDetailService.EntityDetailView;
import io.yak.ops.business.metadata.governance.MetadataGovernanceQueryService.ChangeView;
import io.yak.ops.common.constant.metadata.MetadataPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实体详情与批量取实体（ticket 118）。
 *
 * <p>控制器只做参数拆装：面板构成、分区容错、provider 寻址都在 {@link EntityDetailService}。
 * 详情<b>永远 200</b>（除实体不存在），某块读不了在那块的状态里说（§10 的分区容错口径）。
 *
 * <p>存储量不在这里：那是 lifecycle 的表快照端点，前端当独立块调（本模块不依赖 lifecycle，
 * 否则与 modeling→metadata 那条 SPI 边成环）。
 */
@Tag(name = "元数据-实体详情")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metadata")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetadataPermissionCode.READ)
public class MetadataEntityController {

  private final EntityDetailService detailService;

  @Operation(summary = "实体详情：目录事实 + 分区（按 typeName 出不同面板，单块失败只降级该块）")
  @GetMapping("/entities/{id}")
  public Result<EntityDetailView> detail(@PathVariable("id") long id) {
    return Result.success(detailService.detail(id));
  }

  @Operation(summary = "批量取实体（选择器/列表内联）：一条 IN，上限 200，已撤销的不返回")
  @GetMapping("/entities")
  public Result<List<EntityDTO>> list(@RequestParam("ids") List<Long> ids) {
    return Result.success(detailService.list(ids));
  }

  @Operation(summary = "变更历史分页（详情时间线的\"更多\"）")
  @GetMapping("/entities/{id}/changes")
  public Result<PagingData<ChangeView>> changes(
      @PathVariable("id") long id,
      @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
    return Result.success(PagingData.from(detailService.changes(id, pageNo, pageSize)));
  }
}
