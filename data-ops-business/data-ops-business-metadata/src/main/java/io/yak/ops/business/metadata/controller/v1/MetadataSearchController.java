package io.yak.ops.business.metadata.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metadata.query.MetadataSearchService;
import io.yak.ops.business.metadata.query.MetadataSearchService.HttpRequest;
import io.yak.ops.business.metadata.query.MetadataSearchService.SearchResultView;
import io.yak.ops.common.constant.metadata.MetadataPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨类型统一搜索（ticket 117，plan §4.1/§4.2）。
 *
 * <p>物理表 + 物理列 + 模型 + 标准字段 + 业务域 + 指标在<b>一次查询</b>里返回混合结果，
 * 类型是 facet 而不是多入口——这是本次需求修正的核心诉求，参数面照搬 OM {@code /v1/search/query}
 * 的子集并在 {@link MetadataSearchService} 里由元模型解释。
 *
 * <p>与同前缀的 {@code /types}、{@code /slots} 不同，本端点是 {@code PROJECT_REQUIRED}：
 * 目录行按项目隔离，搜索永远是"在当前空间里找"。
 */
@Tag(name = "元数据-统一搜索")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metadata")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetadataPermissionCode.READ)
public class MetadataSearchController {

  private final MetadataSearchService searchService;

  @Operation(summary = "统一搜索：q 空 = 浏览模式；index 多值 = 类型切换（不换接口，只换 facet）")
  @GetMapping("/search")
  public Result<SearchResultView> search(
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "index", required = false) List<String> index,
      @RequestParam(value = "queryFilter", required = false) String queryFilter,
      @RequestParam(value = "postFilter", required = false) String postFilter,
      @RequestParam(value = "includeFields", required = false) List<String> includeFields,
      @RequestParam(value = "excludeFields", required = false) List<String> excludeFields,
      @RequestParam(value = "sortField", required = false) String sortField,
      @RequestParam(value = "sortOrder", required = false) String sortOrder,
      @RequestParam(value = "searchAfter", required = false) String searchAfter,
      @RequestParam(value = "from", required = false) Integer from,
      @RequestParam(value = "size", required = false) Integer size,
      @RequestParam(value = "getHierarchy", defaultValue = "false") boolean getHierarchy,
      @RequestParam(value = "trackTotalHits", defaultValue = "false") boolean trackTotalHits,
      @RequestParam(value = "explain", defaultValue = "false") boolean explain) {
    return Result.success(searchService.search(new HttpRequest(
        q, index, queryFilter, postFilter, includeFields, excludeFields,
        sortField, sortOrder, searchAfter, from, size,
        getHierarchy, trackTotalHits, explain)));
  }
}
