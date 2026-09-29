package io.yak.ops.business.asset.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.asset.application.AssetSourceLookupService;
import io.yak.ops.business.asset.application.AssetSourceLookupService.SourceLookup;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exact owning-domain identity lookup into the Asset Registry projection. */
@Tag(name = "数据资产-来源身份查询")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/assets/source-lookup")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.READ)
public class AssetSourceLookupController {

  private final AssetSourceLookupService service;

  @Operation(summary = "按来源域稳定 identity 查询 Asset Registry 投影")
  @GetMapping
  public Result<SourceLookup> lookup(
      @RequestParam("sourceType") @NotBlank @Size(max = 32) String sourceType,
      @RequestParam("sourceId") @NotBlank @Size(max = 128) String sourceId) {
    return Result.success(service.lookup(sourceType, sourceId));
  }
}
