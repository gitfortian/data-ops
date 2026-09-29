package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.dataservice.domain.DataServiceQueryResponse;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.execution.DataServiceInvoker;
import io.yak.ops.business.dataservice.observability.DataServiceCallLogReader;
import io.yak.ops.business.dataservice.runtime.DataServiceRuntimePolicyManager;
import io.yak.ops.business.dataservice.runtime.DataServiceRuntimeSnapshot;
import io.yak.ops.business.dataservice.runtime.RuntimePolicyInput;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.dataservice.DataServicePermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Project-scoped Data Service runtime management and observability endpoints. */
@Tag(name = "数据服务")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class DataServiceRuntimeController {
  private final DataServiceRuntimePolicyManager runtimeManager;
  private final DataServiceInvoker invoker;
  private final DataServiceCallLogReader callLogReader;

  @Operation(summary = "查询数据服务 Runtime 状态与指标")
  @RequiresPermission(DataServicePermissionCode.RUNTIME)
  @GetMapping("/{id}/runtime")
  public Result<DataServiceRuntimeSnapshot> runtimeStatus(@PathVariable("id") Long id) {
    return Result.success(runtimeManager.snapshot(id));
  }

  @Operation(summary = "更新数据服务 Runtime 缓存与熔断策略")
  @RequiresPermission(DataServicePermissionCode.RUNTIME)
  @PutMapping("/{id}/runtime")
  public Result<DataServiceRuntimeSnapshot> updateRuntime(
      @PathVariable("id") Long id,
      @RequestBody RuntimePolicyInput input) {
    return Result.success(runtimeManager.update(id, input));
  }

  @Operation(summary = "测试 API 服务")
  @RequiresPermission(DataServicePermissionCode.RUNTIME)
  @PostMapping("/{id}/test")
  public Result<DataServiceQueryResponse> test(
      @PathVariable("id") Long id,
      @RequestBody(required = false) Map<String, String> parameters) {
    return Result.success(invoker.test(id, parameters));
  }

  @Operation(summary = "查询最近调用记录")
  @RequiresPermission(DataServicePermissionCode.OBSERVE)
  @GetMapping("/logs/recent")
  public Result<List<InvocationRecord>> logs() {
    return Result.success(callLogReader.recent());
  }

  @Operation(summary = "查询指定数据服务最近调用记录")
  @RequiresPermission(DataServicePermissionCode.OBSERVE)
  @GetMapping("/{id}/logs")
  public Result<List<InvocationRecord>> logsByApi(
      @PathVariable("id") Long id,
      @RequestParam(value = "limit", defaultValue = "50") int limit) {
    return Result.success(callLogReader.recentByApi(id, limit));
  }
}
