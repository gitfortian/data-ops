package io.yak.ops.business.semantic.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.semantic.api.SemanticFieldApi;
import io.yak.ops.business.semantic.api.SemanticProcessApi;
import io.yak.ops.business.semantic.controller.v1.converter.BusinessProcessViewConverter;
import io.yak.ops.business.semantic.controller.v1.converter.StandardFieldViewConverter;
import io.yak.ops.business.semantic.controller.v1.dto.SemanticProcessQueryDTO;
import io.yak.ops.business.semantic.controller.v1.vo.BusinessProcessVO;
import io.yak.ops.business.semantic.controller.v1.vo.StandardFieldVO;
import io.yak.ops.business.semantic.field.SemanticFieldService;
import io.yak.ops.business.semantic.process.BusinessProcessService;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Business-semantic process management (ticket 34). */
@Tag(name = "业务语义业务过程接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/semantic/processes")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SemanticPermissionCode.READ)
public class SemanticProcessController {

  private final BusinessProcessService service;
  private final io.yak.ops.business.semantic.repository.SemanticProcessFieldRepository processFieldRepository;
  private final SemanticFieldService fieldService;
  private final BusinessProcessViewConverter viewConverter;
  private final StandardFieldViewConverter fieldViewConverter;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "创建业务过程")
  @RequiresPermission(SemanticPermissionCode.CREATE)
  @PostMapping
  public Result<BusinessProcessVO> create(
      @Valid @RequestBody SemanticProcessApi.CreateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        viewConverter.toView(
            service.create(
                request.domainId(),
                request.code(),
                request.name(),
                request.grain(),
                request.bizType(),
                request.owner(),
                request.description(),
                request.sortOrder(),
                operator)));
  }

  @Operation(summary = "分页查询业务过程（可按业务域/类型筛选）")
  @PostMapping("/page")
  public Result<PagingData<BusinessProcessVO>> page(
      @Valid @RequestBody SemanticProcessQueryDTO query) {
    return Result.success(
        viewConverter.page(
            service.page(
                query.getPageNo(), query.getPageSize(), query.getDomainId(),
                query.getKeyword(), query.getBizType())));
  }

  public record ProcessFieldCountView(Long processId, long fieldCount) {}

  @Operation(summary = "各业务过程引用字段数（服务端聚合）")
  @GetMapping("/field-counts")
  public Result<List<ProcessFieldCountView>> fieldCounts() {
    return Result.success(
        processFieldRepository.countByProjectGroupedByProcess().stream()
            .map(count -> new ProcessFieldCountView(count.processId(), count.fieldCount()))
            .toList());
  }

  @Operation(summary = "业务过程详情")
  @GetMapping("/{id}")
  public Result<BusinessProcessVO> get(@PathVariable("id") Long id) {
    return Result.success(viewConverter.toView(service.get(id)));
  }

  @Operation(summary = "按业务域列出业务过程")
  @GetMapping("/by-domain/{domainId}")
  public Result<java.util.List<BusinessProcessVO>> listByDomain(
      @PathVariable("domainId") Long domainId) {
    return Result.success(
        service.listByDomain(domainId).stream().map(viewConverter::toView).toList());
  }

  @Operation(summary = "编辑业务过程（编码不可改）")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<BusinessProcessVO> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody SemanticProcessApi.UpdateRequest request) {
    service.update(
        id,
        request.name(),
        request.domainId(),
        request.grain(),
        request.bizType(),
        request.owner(),
        request.description(),
        request.sortOrder());
    return Result.success(viewConverter.toView(service.get(id)));
  }

  @Operation(summary = "删除业务过程")
  @RequiresPermission(SemanticPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "按过程内顺序列出标准字段（35）")
  @GetMapping("/{id}/fields")
  public Result<java.util.List<StandardFieldVO>> listFields(@PathVariable("id") Long id) {
    return Result.success(
        fieldService.fieldsOfProcess(id).stream().map(fieldViewConverter::toView).toList());
  }

  public record BindFieldRequest(@NotNull(message = "是否必需不能为空") Boolean isRequired) {}

  @Operation(summary = "过程绑定标准字段（追加到末尾；is_required 驱动 44 默认勾选）")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PostMapping("/{id}/fields/{fieldId}")
  public Result<Boolean> bindField(
      @PathVariable("id") Long processId,
      @PathVariable("fieldId") Long fieldId,
      @org.springframework.web.bind.annotation.RequestBody @Valid BindFieldRequest request) {
    fieldService.bindToProcess(processId, fieldId, Boolean.TRUE.equals(request.isRequired()));
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "过程解绑标准字段")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @DeleteMapping("/{id}/fields/{fieldId}")
  public Result<Boolean> unbindField(
      @PathVariable("id") Long processId, @PathVariable("fieldId") Long fieldId) {
    fieldService.unbindFromProcess(processId, fieldId);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "过程字段重排序")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PostMapping("/{id}/fields/reorder")
  public Result<Boolean> reorderFields(
      @PathVariable("id") Long id, @Valid @RequestBody SemanticFieldApi.ReorderRequest request) {
    fieldService.reorderProcessFields(id, request.orderedFieldIds());
    return Result.success(Boolean.TRUE);
  }
}
