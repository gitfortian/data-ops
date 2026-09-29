package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.application.MdmProcessingTaskService;
import io.yak.ops.business.mdm.application.MdmRecordService;
import io.yak.ops.business.mdm.controller.v1.dto.MdmRecordQueryDTO;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.common.constant.mdm.MdmPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Master data record read side + processing SQL generation (ticket 55a). */
@Tag(name = "主数据记录接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mdm/entities/{entityId}/records")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MdmPermissionCode.READ)
public class MdmRecordController {

  private final MdmRecordService service;
  private final MdmProcessingTaskService processingTaskService;

  @Operation(summary = "主数据记录分页查询(只读)")
  @PostMapping("/page")
  public Result<PagingData<MdmRecord>> page(
      @PathVariable("entityId") Long entityId,
      @Valid @RequestBody MdmRecordQueryDTO query) {
    return Result.success(
        PagingData.from(
            service.page(entityId, query.getPageNo(), query.getPageSize(),
                query.getKeyword(), query.getStatus())));
  }

  @Operation(summary = "生成主数据加工 SQL(落地表→yak_mdm_record,同库可执行)")
  @PostMapping("/master-sql")
  public Result<String> generateMasterSql(@PathVariable("entityId") Long entityId) {
    return Result.success(service.generateMasterSql(entityId));
  }

  @Operation(summary = "注册主数据加工任务草稿(R2:find-or-create SQL 节点+saveDraft,返回节点供跳转)")
  @RequiresPermission(MdmPermissionCode.CREATE)
  @PostMapping("/processing-task")
  public Result<MdmProcessingTaskService.ProcessingTaskReceipt> registerProcessingTask(
      @PathVariable("entityId") Long entityId) {
    return Result.success(processingTaskService.register(entityId));
  }
}
