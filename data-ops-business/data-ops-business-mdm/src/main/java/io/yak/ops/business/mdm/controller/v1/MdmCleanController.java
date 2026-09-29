package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.api.MdmCleanApi;
import io.yak.ops.business.mdm.application.MdmCleanService;
import io.yak.ops.business.mdm.application.MdmCleanService.DedupGroup;
import io.yak.ops.business.mdm.application.MdmCleanService.MergePreview;
import io.yak.ops.business.mdm.application.MdmCleanService.TransformPreview;
import io.yak.ops.business.mdm.controller.v1.vo.MdmCleanRuleVO;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRule;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleType;
import io.yak.ops.business.mdm.domain.clean.MdmDedupIgnore;
import io.yak.ops.business.mdm.domain.clean.MdmMergeLog;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Master data cleansing: dedup/standardize/complete rules, dedup discovery, merge (ticket 56/57). */
@Tag(name = "主数据清洗接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mdm/clean")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MdmPermissionCode.READ)
public class MdmCleanController {

  private final MdmCleanService service;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "清洗规则列表(按实体,可选按规则类型过滤)")
  @GetMapping("/rules")
  public Result<List<MdmCleanRuleVO>> listRules(
      @RequestParam("entityId") Long entityId,
      @RequestParam(value = "ruleType", required = false) String ruleType) {
    return Result.success(
        service.listRules(entityId, ruleType).stream().map(MdmCleanRuleVO::from).toList());
  }

  @Operation(summary = "创建清洗规则(通用:支持 DEDUP/STANDARDIZE/COMPLETE)")
  @RequiresPermission(MdmPermissionCode.CREATE)
  @PostMapping("/rules/typed")
  public Result<MdmCleanRuleVO> createTyped(
      @Valid @RequestBody MdmCleanApi.TypedRuleSaveRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    MdmCleanRule rule =
        service.createGeneric(
            request.entityId(),
            request.ruleType(),
            request.ruleName(),
            request.ruleExpr(),
            request.sortOrder(),
            operator);
    return Result.success(MdmCleanRuleVO.from(rule));
  }

  @Operation(summary = "编辑清洗规则(通用)")
  @RequiresPermission(MdmPermissionCode.UPDATE)
  @PutMapping("/rules/{id}/typed")
  public Result<Boolean> updateTyped(
      @PathVariable("id") Long id,
      @Valid @RequestBody MdmCleanApi.TypedRuleUpdateRequest request) {
    service.updateGeneric(id, request.ruleName(), request.ruleExpr(), request.sortOrder());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "创建去重规则(规则表达式 JSON)")
  @RequiresPermission(MdmPermissionCode.CREATE)
  @PostMapping("/rules")
  public Result<MdmCleanRuleVO> create(
      @Valid @RequestBody MdmCleanApi.RuleSaveRequest request, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    MdmCleanRule rule =
        service.create(
            request.entityId(),
            request.ruleName(),
            request.ruleExpr(),
            request.sortOrder(),
            operator);
    return Result.success(MdmCleanRuleVO.from(rule));
  }

  @Operation(summary = "编辑去重规则")
  @RequiresPermission(MdmPermissionCode.UPDATE)
  @PutMapping("/rules/{id}")
  public Result<Boolean> update(
      @PathVariable("id") Long id, @Valid @RequestBody MdmCleanApi.RuleUpdateRequest request) {
    service.update(id, request.ruleName(), request.ruleExpr(), request.sortOrder());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "启停去重规则")
  @RequiresPermission(MdmPermissionCode.UPDATE)
  @PostMapping("/rules/{id}/enabled")
  public Result<Boolean> setEnabled(
      @PathVariable("id") Long id, @RequestBody MdmCleanApi.EnabledRequest request) {
    service.setEnabled(id, request.enabled());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除去重规则")
  @RequiresPermission(MdmPermissionCode.DELETE)
  @DeleteMapping("/rules/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "执行去重发现(服务端聚合,分页返回重复组)")
  @PostMapping("/dedup/discover")
  public Result<PagingData<DedupGroup>> discover(
      @Valid @RequestBody MdmCleanApi.DedupRunRequest request) {
    return Result.success(
        PagingData.from(
            service.findDuplicates(
                request.entityId(),
                request.ruleId(),
                request.pageNo() == null ? 1 : request.pageNo(),
                request.pageSize() == null ? 10 : request.pageSize())));
  }

  @Operation(summary = "忽略重复组(判定已知非重复,该规则下的去重发现不再返回此组)")
  @RequiresPermission(MdmPermissionCode.UPDATE)
  @PostMapping("/dedup/ignore")
  public Result<MdmDedupIgnore> ignore(
      @Valid @RequestBody MdmCleanApi.DedupIgnoreRequest request, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        service.ignoreDedupGroup(
            request.entityId(),
            request.ruleId(),
            request.matchKey(),
            request.matchBasis(),
            request.reason(),
            operator));
  }

  @Operation(summary = "已忽略重复组(按实体 + 去重规则)")
  @GetMapping("/dedup/ignores")
  public Result<List<MdmDedupIgnore>> ignores(
      @RequestParam("entityId") Long entityId, @RequestParam("ruleId") Long ruleId) {
    return Result.success(service.listIgnoredGroups(entityId, ruleId));
  }

  @Operation(summary = "撤销忽略(该组键重新回到去重发现结果)")
  @RequiresPermission(MdmPermissionCode.UPDATE)
  @DeleteMapping("/dedup/ignore/{id}")
  public Result<Boolean> unignore(@PathVariable("id") Long id) {
    service.unignoreDedupGroup(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "合并预览(属性/source_ids 对比与合并结果)")
  @PostMapping("/merge/preview")
  public Result<MergePreview> preview(
      @Valid @RequestBody MdmCleanApi.MergePreviewRequest request) {
    return Result.success(
        service.previewMerge(
            request.entityId(), request.masterRecordId(), request.mergedRecordIds()));
  }

  @Operation(summary = "执行合并(不可回滚,记录可追溯)")
  @RequiresPermission(MdmPermissionCode.UPDATE)
  @PostMapping("/merge")
  public Result<Boolean> merge(
      @Valid @RequestBody MdmCleanApi.MergeExecuteRequest request, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    service.executeMerge(
        request.entityId(),
        request.ruleId(),
        request.masterRecordId(),
        request.mergedRecordIds(),
        operator);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "标准化/补全预览(返回受影响记录数与变更明细)")
  @GetMapping("/transform/{ruleId}/preview")
  public Result<TransformPreview> previewTransform(
      @PathVariable("ruleId") Long ruleId,
      @RequestParam("entityId") Long entityId) {
    return Result.success(service.previewTransform(entityId, ruleId));
  }

  @Operation(summary = "执行标准化/补全(批量更新受影响记录)")
  @RequiresPermission(MdmPermissionCode.UPDATE)
  @PostMapping("/transform/apply")
  public Result<Integer> applyTransform(
      @Valid @RequestBody MdmCleanApi.TransformApplyRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(service.applyTransform(request.entityId(), request.ruleId(), operator));
  }

  @Operation(summary = "合并日志(按实体)")
  @GetMapping("/merge-logs")
  public Result<List<MdmMergeLog>> mergeLogs(@RequestParam("entityId") Long entityId) {
    return Result.success(service.mergeLogs(entityId));
  }
}
