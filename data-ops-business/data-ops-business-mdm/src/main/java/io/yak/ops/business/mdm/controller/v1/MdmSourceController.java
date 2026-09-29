package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.api.MdmSourceApi;
import io.yak.ops.business.mdm.application.MdmSourceService;
import io.yak.ops.business.mdm.application.MdmSourceService.TableCandidate;
import io.yak.ops.business.mdm.controller.v1.converter.MdmSourceViewConverter;
import io.yak.ops.business.mdm.controller.v1.vo.MdmSourceVO;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import io.yak.ops.common.constant.mdm.MdmPermissionCode;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Master data identification and source binding (ticket 53). */
@Tag(name = "主数据识别接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mdm")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MdmPermissionCode.READ)
public class MdmSourceController {

  private final MdmSourceService service;
  private final MdmSourceViewConverter viewConverter;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "扫描数据源表清单(标注候选实体与已确认)")
  @GetMapping("/identification/tables")
  public Result<List<TableCandidate>> scan(
      @RequestParam("datasourceId") Long datasourceId,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return Result.success(service.scan(datasourceId, keyword, limit));
  }

  @Operation(summary = "已确认来源列表(可按实体筛选)")
  @GetMapping("/sources")
  public Result<List<MdmSourceVO>> listSources(
      @RequestParam(value = "entityId", required = false) Long entityId) {
    return Result.success(viewConverter.toViews(service.listSources(entityId)));
  }

  @Operation(summary = "确认来源绑定(生成 MAIN/AUXILIARY 来源)")
  @RequiresPermission(MdmPermissionCode.CREATE)
  @PostMapping("/sources")
  public Result<MdmSourceVO> confirm(
      @Valid @RequestBody MdmSourceApi.ConfirmRequest request, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    MdmSource source =
        service.confirm(
            request.entityId(),
            request.datasourceId(),
            request.database(),
            request.schema(),
            request.table(),
            request.toRole(),
            request.fieldMapping(),
            operator);
    return Result.success(viewConverter.toView(source));
  }

  @Operation(summary = "解绑来源(被采集配置引用时阻断)")
  @RequiresPermission(MdmPermissionCode.DELETE)
  @DeleteMapping("/sources/{id}")
  public Result<Boolean> unbind(@PathVariable("id") Long id) {
    service.unbind(id);
    return Result.success(Boolean.TRUE);
  }
}
