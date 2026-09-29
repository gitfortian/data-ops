package io.yak.ops.business.metric.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metric.tag.MetricTagService;
import io.yak.ops.common.bean.po.metric.MetricTagPO;
import io.yak.ops.common.bean.po.metric.MetricTagRelPO;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 指标标签管理 REST API（T49）。 */
@Tag(name = "指标标签接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metrics/tags")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricTagController {

  private final MetricTagService tagService;
  private final CurrentUserProvider currentUserProvider;

  @Data
  public static class TagCreateRequest {
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 128, message = "标签名称不能超过 128 个字符")
    String tagName;
  }

  @Data
  public static class TagUpdateRequest {
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 128, message = "标签名称不能超过 128 个字符")
    String tagName;
    Integer sortOrder;
  }

  @Data
  public static class TagAssignRequest {
    @NotNull(message = "标签ID列表不能为空")
    @NotEmpty(message = "标签ID列表不能为空")
    List<@NotNull(message = "标签ID不能为null") Long> tagIds;
  }

  public record TagView(Long id, String tagCode, String tagName,
      Integer sortOrder, String status) {
    public static TagView from(MetricTagPO po) {
      return new TagView(po.getId(), po.getTagCode(), po.getTagName(),
          po.getSortOrder(), po.getStatus());
    }
  }

  @Operation(summary = "查询标签列表")
  @GetMapping
  public Result<List<TagView>> list() {
    return Result.success(tagService.listTags().stream()
        .map(TagView::from).toList());
  }

  @Operation(summary = "新建标签")
  @RequiresPermission(MetricPermissionCode.CREATE)
  @PostMapping
  public Result<TagView> create(
      @Valid @RequestBody TagCreateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(TagView.from(tagService.createTag(request.getTagName(), operator)));
  }

  @Operation(summary = "编辑标签")
  @RequiresPermission(MetricPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<Boolean> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody TagUpdateRequest request) {
    tagService.updateTag(id, request.getTagName(), request.getSortOrder());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除标签(有指标挂载时阻断)")
  @RequiresPermission(MetricPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    tagService.deleteTag(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "给指标打标签")
  @RequiresPermission(MetricPermissionCode.UPDATE)
  @PostMapping("/assign/{metricId}")
  public Result<Boolean> assign(
      @PathVariable("metricId") Long metricId,
      @Valid @RequestBody TagAssignRequest request) {
    tagService.batchAssignTags(metricId, request.getTagIds());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "从指标移除标签")
  @RequiresPermission(MetricPermissionCode.UPDATE)
  @DeleteMapping("/remove/{metricId}/{tagId}")
  public Result<Boolean> remove(
      @PathVariable("metricId") Long metricId,
      @PathVariable("tagId") Long tagId) {
    tagService.removeTag(metricId, tagId);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "查询指标的标签")
  @GetMapping("/metric/{metricId}")
  public Result<List<Long>> metricTags(@PathVariable("metricId") Long metricId) {
    return Result.success(tagService.listTagsByMetric(metricId).stream()
        .map(MetricTagRelPO::getTagId).toList());
  }
}
