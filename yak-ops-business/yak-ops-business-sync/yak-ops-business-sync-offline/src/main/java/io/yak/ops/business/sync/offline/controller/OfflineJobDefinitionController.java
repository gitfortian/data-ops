package io.yak.ops.business.sync.offline.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.definition.OfflineJobRevisionService;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionQueryDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobDefinitionVO;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
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
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnOfflineSyncEnabled
@RestController
@RequestMapping("/api/v1/job/batch-definition")
@RequiredArgsConstructor
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class OfflineJobDefinitionController {

  private final OfflineJobDefinitionService service;

  @GetMapping("/get-unique-id")
  public Result<Long> nextId() { return Result.success(service.nextId()); }

  @PostMapping("/draft")
  public Result<Long> saveDraft(@RequestBody OfflineJobDefinitionDTO requestDTO) {
    return Result.success(service.saveDraft(requestDTO));
  }

  @PostMapping({"/guide-single/saveOrUpdate", "/guide-multi/saveOrUpdate"})
  public Result<Long> saveGuide(@RequestBody OfflineJobDefinitionDTO requestDTO) {
    return Result.success(service.saveGuide(requestDTO));
  }

  @PostMapping({"/guide-single/build-config", "/guide-multi/build-config", "/build-job-spec"})
  public Result<String> buildGuideConfig(@RequestBody OfflineJobDefinitionDTO requestDTO) {
    return Result.success(service.buildGuideConfig(requestDTO));
  }

  @GetMapping("/{id}")
  public Result<OfflineJobDefinitionVO> get(@PathVariable Long id) {
    return Result.success(service.get(id));
  }

  @GetMapping("/{id}/edit-detail")
  public Result<JsonNode> editDetail(@PathVariable Long id) {
    return Result.success(service.getEditDetail(id));
  }

  @PostMapping("/page")
  public Result<PagingData<OfflineJobDefinitionVO>> page(
      @Valid @RequestBody(required = false) OfflineJobDefinitionQueryDTO queryDTO) {
    return Result.success(service.page(queryDTO));
  }

  @PutMapping("/{id}/online")
  public Result<Boolean> online(@PathVariable Long id) { return Result.success(service.online(id)); }

  @PutMapping("/{id}/offline")
  public Result<Boolean> offline(@PathVariable Long id) { return Result.success(service.offline(id)); }

  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable Long id) { return Result.success(service.delete(id)); }

  @PostMapping("/{id}/publish")
  public Result<OfflineJobRevisionService.PublishResult> publish(@PathVariable Long id) {
    return Result.success(service.publish(id));
  }

  @GetMapping("/{id}/versions")
  public Result<List<OfflineJobRevisionService.VersionSummary>> versions(@PathVariable Long id) {
    return Result.success(service.versions(id));
  }

  @GetMapping("/{id}/versions/{versionNo}")
  public Result<OfflineJobRevisionService.RevisionDetailView> version(
      @PathVariable Long id, @PathVariable int versionNo) {
    return Result.success(service.versionDetail(id, versionNo));
  }

  @PostMapping("/{id}/versions/{versionNo}/rollback")
  public Result<OfflineJobRevisionService.PublishResult> rollback(
      @PathVariable Long id, @PathVariable int versionNo) {
    return Result.success(service.rollback(id, versionNo));
  }
}
