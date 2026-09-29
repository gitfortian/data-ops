package io.yak.ops.business.asset.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.asset.catalog.TagService;
import io.yak.ops.business.asset.catalog.TagService.TagView;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.AddTagsDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.TagUpsertDTO;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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

/** 业务标签 REST API(ticket 93)。 */
@Tag(name = "数据资产-标签")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/assets")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.READ)
public class AssetTagController {

  private final TagService tagService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "标签字典列表")
  @GetMapping("/tags")
  public Result<List<TagView>> list(@RequestParam(value = "keyword", required = false)
      String keyword) {
    return Result.success(tagService.list(keyword));
  }

  @Operation(summary = "新建标签(tagCode 空自动生成)")
  @RequiresPermission(AssetPermissionCode.CREATE)
  @PostMapping("/tags")
  public Result<TagView> create(
      @Valid @RequestBody TagUpsertDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        tagService.create(dto.getTagCode(), dto.getTagName(), dto.getColor(),
            dto.getDescription(), operator));
  }

  @Operation(summary = "编辑标签")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PutMapping("/tags/{tagId}")
  public Result<TagView> update(
      @PathVariable("tagId") Long tagId,
      @Valid @RequestBody TagUpsertDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        tagService.update(tagId, dto.getTagCode(), dto.getTagName(), dto.getColor(),
            dto.getDescription(), operator));
  }

  @Operation(summary = "删除标签(关系一并物理删)")
  @RequiresPermission(AssetPermissionCode.DELETE)
  @DeleteMapping("/tags/{tagId}")
  public Result<Boolean> deleteTag(@PathVariable("tagId") Long tagId,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    tagService.delete(tagId, operator);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "资产已打标签列表")
  @GetMapping("/{id}/tags")
  public Result<List<TagView>> tagsOfAsset(@PathVariable("id") Long id) {
    return Result.success(tagService.tagsOfAsset(id));
  }

  @Operation(summary = "资产打标(批量幂等)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/{id}/tags")
  public Result<Integer> attach(
      @PathVariable("id") Long id,
      @Valid @RequestBody AddTagsDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(tagService.attach(id, dto.getTagIds(), operator));
  }

  @Operation(summary = "资产去标")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @DeleteMapping("/{id}/tags/{tagId}")
  public Result<Integer> detach(@PathVariable("id") Long id, @PathVariable("tagId") Long tagId,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(tagService.detach(id, tagId, operator));
  }
}
