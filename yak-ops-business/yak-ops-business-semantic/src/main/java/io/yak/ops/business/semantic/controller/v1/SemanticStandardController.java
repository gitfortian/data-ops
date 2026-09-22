package io.yak.ops.business.semantic.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.semantic.api.SemanticStandardApi;
import io.yak.ops.business.semantic.approval.StandardPublishApprovalService;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import io.yak.ops.business.semantic.controller.v1.converter.StandardViewConverter;
import io.yak.ops.business.semantic.controller.v1.dto.SemanticStandardQueryDTO;
import io.yak.ops.business.semantic.controller.v1.vo.CodeSetDetailVO;
import io.yak.ops.business.semantic.controller.v1.vo.CodeSetOptionVO;
import io.yak.ops.business.semantic.controller.v1.vo.CodeSetVO;
import io.yak.ops.business.semantic.controller.v1.vo.StandardOptionVO;
import io.yak.ops.business.semantic.controller.v1.vo.StandardVO;
import io.yak.ops.business.semantic.controller.v1.vo.StandardVersionVO;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

/** Business-semantic standard catalog: CRUD/status over the six standard kinds. */
@Tag(name = "业务语义数据标准接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/semantic/standards")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SemanticPermissionCode.READ)
public class SemanticStandardController {

  private final StandardCatalogService service;
  private final StandardViewConverter viewConverter;
  private final CurrentUserProvider currentUserProvider;
  private final StandardPublishApprovalService publishApprovalService;

  @Operation(summary = "创建数据标准")
  @RequiresPermission(SemanticPermissionCode.CREATE)
  @PostMapping
  public Result<StandardVO> create(
      @Valid @RequestBody SemanticStandardApi.CreateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(viewConverter.toView(service.create(request, operator)));
  }

  @Operation(summary = "分页查询数据标准")
  @PostMapping("/page")
  public Result<PagingData<StandardVO>> page(
      @Valid @RequestBody SemanticStandardQueryDTO query) {
    return Result.success(
        viewConverter.page(
            service.page(
                query.getPageNo(), query.getPageSize(), query.getKind(),
                query.getKeyword(), query.getStatus())));
  }

  @Operation(summary = "数据标准详情")
  @GetMapping("/{id}")
  public Result<StandardVO> get(@PathVariable("id") Long id) {
    return Result.success(viewConverter.toView(service.get(id)));
  }

  @Operation(summary = "标准历史版本（修改前快照，最新在前）")
  @GetMapping("/{id}/versions")
  public Result<List<StandardVersionVO>> listVersions(@PathVariable("id") Long id) {
    return Result.success(
        service.listVersions(id).stream().map(viewConverter::toVersionView).toList());
  }

  @Operation(summary = "更新数据标准（编码/类别不可改，版本自增）")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<StandardVO> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody SemanticStandardApi.UpdateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(viewConverter.toView(service.update(id, request, operator)));
  }

  @Operation(summary = "启用/停用数据标准")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PostMapping("/{id}/status")
  public Result<StandardVO> changeStatus(
      @PathVariable("id") Long id,
      @Valid @RequestBody SemanticStandardApi.StatusRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        viewConverter.toView(service.changeStatus(id, request.status(), operator)));
  }

  @Operation(summary = "提交标准生效审批(需已配置 STANDARD_PUBLISH 流程;批准后自动启用)")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PostMapping("/{id}/publish-approval")
  public Result<ApprovalInstanceView> submitPublishApproval(
      @PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(publishApprovalService.submit(id, operator));
  }

  @Operation(summary = "删除数据标准（物理删除，删除前引用校验）")
  @RequiresPermission(SemanticPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    service.delete(id, currentUserProvider.getCurrentUser(httpRequest));
    return Result.success(Boolean.TRUE);
  }

  // ── 码集端点 ──

  @Operation(summary = "批量创建/更新码集")
  @RequiresPermission(SemanticPermissionCode.CREATE)
  @PostMapping("/code-set")
  public Result<List<StandardVO>> saveCodeSet(
      @Valid @RequestBody SemanticStandardApi.CodeSetSaveRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    List<Standard> saved = service.saveCodeSet(request, operator);
    return Result.success(saved.stream().map(viewConverter::toView).toList());
  }

  @Operation(summary = "码集聚合分页")
  @PostMapping("/code-set/page")
  public Result<PagingData<CodeSetVO>> pageCodeSet(
      @Valid @RequestBody SemanticStandardQueryDTO query) {
    return Result.success(
        viewConverter.pageCodeSet(
            service.pageCodeSet(query.getPageNo(), query.getPageSize(), query.getKeyword(), query.getStatus())));
  }

  @Operation(summary = "按组键查码集详情(存量空码集行以 std_code 为组键)")
  @GetMapping("/code-set/{codeSetCode}")
  public Result<CodeSetDetailVO> getCodeSet(@PathVariable("codeSetCode") String codeSetCode) {
    List<Standard> rows = service.getCodeSet(codeSetCode);
    boolean legacy =
        rows.stream()
            .anyMatch(row -> row.fields().codeSetCode() == null || row.fields().codeSetCode().isBlank());
    // 历史数据组内名称不一致:编辑保存后统一为码集名称(32.1 自愈)
    boolean nameInconsistent = rows.stream().map(Standard::name).distinct().count() > 1;
    CodeSetDetailVO detail = viewConverter.toCodeSetDetailView(codeSetCode, rows, legacy);
    detail.setNameInconsistent(nameInconsistent);
    return Result.success(detail);
  }

  @Operation(summary = "码集级历史版本(合并组内各行修改前快照,按时间倒序)")
  @GetMapping("/code-set/{codeSetCode}/versions")
  public Result<List<StandardVersionVO>> listCodeSetVersions(
      @PathVariable("codeSetCode") String codeSetCode) {
    return Result.success(
        service.listCodeSetVersions(codeSetCode).stream().map(viewConverter::toVersionView).toList());
  }

  @Operation(summary = "启用码集选项(标准字段码值引用下拉,value=码集编码)")
  @GetMapping("/code-set/options")
  public Result<List<CodeSetOptionVO>> listCodeSetOptions() {
    return Result.success(
        service.listCodeSetOptions().stream().map(viewConverter::toCodeSetOptionView).toList());
  }

  @Operation(summary = "启用标准选项(字段引用下拉,弹窗打开时按 kinds 按需加载;CODE 走码集选项端点)")
  @GetMapping("/options")
  public Result<Map<String, List<StandardOptionVO>>> listStandardOptions(
      @RequestParam("kinds") String kinds) {
    Map<String, List<StandardOptionVO>> grouped = new LinkedHashMap<>();
    service.listStandardOptions(kinds)
        .forEach((kind, list) -> grouped.put(kind.name(), list.stream().map(viewConverter::toOptionView).toList()));
    return Result.success(grouped);
  }

  @Operation(summary = "码集编码是否存在(新建判重,提示是否转入编辑)")
  @GetMapping("/code-set/{codeSetCode}/exists")
  public Result<Boolean> existsCodeSet(@PathVariable("codeSetCode") String codeSetCode) {
    return Result.success(service.existsCodeSet(codeSetCode));
  }

  @Operation(summary = "整组启用/停用码集")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PostMapping("/code-set/{codeSetCode}/status")
  public Result<Boolean> changeCodeSetStatus(
      @PathVariable("codeSetCode") String codeSetCode,
      @Valid @RequestBody SemanticStandardApi.StatusRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    int count = service.changeCodeSetStatus(codeSetCode, request.status(), operator);
    return Result.success(count > 0);
  }

  @Operation(summary = "整组删除码集")
  @RequiresPermission(SemanticPermissionCode.DELETE)
  @DeleteMapping("/code-set/{codeSetCode}")
  public Result<Boolean> deleteCodeSet(
      @PathVariable("codeSetCode") String codeSetCode,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    int count = service.deleteCodeSet(codeSetCode, operator);
    return Result.success(count > 0);
  }
}
