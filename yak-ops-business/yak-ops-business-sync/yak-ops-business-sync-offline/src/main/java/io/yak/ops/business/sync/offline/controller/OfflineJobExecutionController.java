package io.yak.ops.business.sync.offline.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.execution.OfflineJobExecutionService;
import io.yak.ops.common.bean.dto.sync.offline.OfflineBatchOperationDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobExecutionQueryDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineBatchOperationVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineConnectorRuntimeSnapshotVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineEngineHealthVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionLogPageVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionDetailVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 离线执行命令和执行历史查询接口。 */
@ConditionalOnOfflineSyncEnabled
@RestController
@RequiredArgsConstructor
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class OfflineJobExecutionController {

  private final OfflineJobExecutionService service;

  @GetMapping({"/api/v1/job/batch-execution/health", "/api/v1/executor/health"})
  @ProjectScope(ProjectMigrationMode.LEGACY_GLOBAL)
  public Result<OfflineEngineHealthVO> health() { return Result.success(service.health()); }

  @GetMapping("/api/v1/job/batch-execution/connectors")
  @ProjectScope(ProjectMigrationMode.LEGACY_GLOBAL)
  public Result<OfflineConnectorRuntimeSnapshotVO> connectors() {
    return Result.success(service.connectorRuntime());
  }

  @GetMapping("/api/v1/job/batch-execution/connectors/{connectorId}/schema")
  @ProjectScope(ProjectMigrationMode.LEGACY_GLOBAL)
  public Result<JsonNode> connectorSchema(
      @PathVariable String connectorId,
      @RequestParam String role) {
    return Result.success(service.connectorSchema(connectorId, role));
  }

  @PostMapping("/api/v1/job/batch-execution/{jobDefineId}/execute")
  public Result<OfflineJobExecutionVO> execute(@PathVariable Long jobDefineId) {
    return Result.success(service.execute(jobDefineId));
  }

  @PostMapping("/api/v1/job/batch-execution/{jobInstanceId}/cancel")
  public Result<OfflineJobExecutionVO> cancel(@PathVariable Long jobInstanceId) {
    return Result.success(service.cancel(jobInstanceId));
  }

  @PostMapping("/api/v1/job/batch-execution/{jobInstanceId}/retry")
  public Result<OfflineJobExecutionVO> retry(@PathVariable Long jobInstanceId) {
    return Result.success(service.retry(jobInstanceId));
  }

  @PostMapping({"/api/v1/job/batch-execution/batch-execute", "/api/v1/executor/batch-execute"})
  public Result<OfflineBatchOperationVO> batchExecute(
      @Valid @RequestBody OfflineBatchOperationDTO requestDTO) {
    return Result.success(service.batchExecute(requestDTO));
  }

  @PostMapping({"/api/v1/job/batch-execution/batch-pause", "/api/v1/executor/batch-pause"})
  public Result<OfflineBatchOperationVO> batchPause(
      @Valid @RequestBody OfflineBatchOperationDTO requestDTO) {
    return Result.success(service.batchCancel(requestDTO));
  }

  @PostMapping("/api/v1/job/batch-instance/page")
  public Result<PagingData<OfflineJobExecutionVO>> instancePage(
      @Valid @RequestBody(required = false) OfflineJobExecutionQueryDTO queryDTO) {
    return Result.success(service.page(queryDTO));
  }

  @GetMapping("/api/v1/job/batch-instance/{id}")
  public Result<OfflineJobExecutionDetailVO> instance(@PathVariable Long id) {
    return Result.success(service.detail(id));
  }

  @GetMapping("/api/v1/job/batch-instance/{id}/table-metrics")
  public Result<JsonNode> tableMetrics(@PathVariable Long id) {
    return Result.success(service.tableMetrics(id));
  }

  @GetMapping("/api/v1/job/batch-instance/{id}/log")
  public Result<String> instanceLog(@PathVariable Long id) {
    return Result.success(service.logs(id));
  }

  @GetMapping("/api/v1/job/batch-instance/{id}/logs")
  public Result<OfflineExecutionLogPageVO> instanceLogs(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0:0") String cursor,
      @RequestParam(defaultValue = "500") int limit) {
    return Result.success(service.logs(id, cursor, limit));
  }
}
