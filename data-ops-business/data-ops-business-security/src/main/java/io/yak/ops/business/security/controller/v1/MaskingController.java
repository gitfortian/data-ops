package io.yak.ops.business.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.security.api.MaskingDirective;
import io.yak.ops.business.security.application.MaskingEngine;
import io.yak.ops.business.security.application.MaskingService;
import io.yak.ops.business.security.dao.model.DsecMaskingAlgorithmPO;
import io.yak.ops.business.security.dao.model.DsecMaskingPolicyPO;
import io.yak.ops.common.constant.security.SecurityPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 脱敏算法与策略(票据 76)。 */
@Tag(name = "数据安全-脱敏接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-security/masking")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SecurityPermissionCode.READ)
public class MaskingController {

  private final MaskingService service;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "内置支持的算法编码")
  @GetMapping("/algorithms/supported")
  public Result<Set<String>> supportedAlgorithms() {
    return Result.success(MaskingEngine.supported());
  }

  @Operation(summary = "算法列表")
  @GetMapping("/algorithms")
  public Result<List<DsecMaskingAlgorithmPO>> listAlgorithms() {
    return Result.success(service.listAlgorithms());
  }

  @Operation(summary = "算法详情")
  @GetMapping("/algorithms/{id}")
  public Result<DsecMaskingAlgorithmPO> getAlgorithm(@PathVariable("id") Long id) {
    return Result.success(service.getAlgorithm(id));
  }

  @Operation(summary = "创建算法")
  @RequiresPermission(SecurityPermissionCode.CREATE)
  @PostMapping("/algorithms")
  public Result<DsecMaskingAlgorithmPO> createAlgorithm(@Valid @RequestBody AlgoCreateRequest body,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        service.createAlgorithm(body.code(), body.name(), body.params(), body.description(), operator));
  }

  @Operation(summary = "删除算法(内置算法阻断)")
  @RequiresPermission(SecurityPermissionCode.DELETE)
  @DeleteMapping("/algorithms/{id}")
  public Result<Boolean> deleteAlgorithm(@PathVariable("id") Long id) {
    service.deleteAlgorithm(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "脱敏策略分页")
  @PostMapping("/policies/page")
  public Result<PagingData<DsecMaskingPolicyPO>> pagePolicies(@Valid @RequestBody PageQuery query) {
    return Result.success(
        PagingData.from(service.pagePolicies(query.pageNo(), query.pageSize(), query.keyword())));
  }

  @Operation(summary = "脱敏策略详情")
  @GetMapping("/policies/{id}")
  public Result<DsecMaskingPolicyPO> getPolicy(@PathVariable("id") Long id) {
    return Result.success(service.getPolicy(id));
  }

  @Operation(summary = "创建脱敏策略")
  @RequiresPermission(SecurityPermissionCode.CREATE)
  @PostMapping("/policies")
  public Result<DsecMaskingPolicyPO> createPolicy(@Valid @RequestBody PolicyCreateRequest body,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(service.createPolicy(body.name(), body.levelId(), body.categoryId(),
        body.columnPattern(), body.algoId(), body.priority(), body.enabled(), body.description(),
        operator));
  }

  @Operation(summary = "删除脱敏策略")
  @RequiresPermission(SecurityPermissionCode.DELETE)
  @DeleteMapping("/policies/{id}")
  public Result<Boolean> deletePolicy(@PathVariable("id") Long id) {
    service.deletePolicy(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "解析对象的脱敏指令")
  @GetMapping("/resolve")
  public Result<MaskingDirective> resolve(@RequestParam("objectKey") String objectKey) {
    return Result.success(service.resolve(objectKey));
  }

  @Operation(summary = "试算脱敏结果")
  @PostMapping("/preview")
  public Result<String> preview(@Valid @RequestBody PreviewRequest body) {
    return Result.success(service.mask(body.value(), body.algoCode(), body.algoParams()));
  }

  public record PageQuery(int pageNo, int pageSize, String keyword) {}

  public record AlgoCreateRequest(@NotBlank String code, @NotBlank String name, String params,
      String description) {}

  public record PolicyCreateRequest(@NotBlank String name, Long levelId, Long categoryId,
      String columnPattern, Long algoId, Integer priority, Boolean enabled, String description) {}

  public record PreviewRequest(String value, @NotBlank String algoCode, String algoParams) {}
}
