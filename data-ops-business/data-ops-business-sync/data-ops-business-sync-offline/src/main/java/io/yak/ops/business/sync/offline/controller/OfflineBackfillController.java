package io.yak.ops.business.sync.offline.controller;

import io.yak.framework.common.Result;
import io.yak.ops.business.sync.offline.backfill.OfflineBackfillService;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.common.bean.dto.sync.offline.OfflineBackfillRequestDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineBackfillVO;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnOfflineSyncEnabled
@RestController
@RequiredArgsConstructor
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class OfflineBackfillController {

  private final OfflineBackfillService backfillService;

  @PostMapping("/api/v1/job/batch-execution/{jobDefineId}/backfill")
  public Result<OfflineBackfillVO> backfill(
      @PathVariable Long jobDefineId,
      @Valid @RequestBody OfflineBackfillRequestDTO requestDTO) {
    return Result.success(backfillService.submit(jobDefineId, requestDTO));
  }
}
