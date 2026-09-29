package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.dataservice.documentation.DataServiceDocumentationManager;
import io.yak.ops.business.dataservice.management.DataServiceManager;
import io.yak.ops.business.dataservice.publication.DataServicePublicationReader;
import io.yak.ops.business.dataservice.publication.DataServicePublisher;
import io.yak.ops.business.dataservice.publication.PublicationSettings;
import io.yak.ops.business.dataservice.publication.PublishRequest;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.SourcePage;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.dataservice.DataServicePermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
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

/** Data Service definition and publication management endpoints. */
@Tag(name = "数据服务")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class DataServiceController {
  private final DataServiceReader reader;
  private final DataServiceViewFactory viewFactory;
  private final DataServicePublicationReader publicationReader;
  private final DataServicePublisher publisher;
  private final DataServiceManager manager;
  private final DataServiceDocumentationManager documentationManager;

  @Operation(summary = "查询 API 服务列表")
  @RequiresPermission(DataServicePermissionCode.READ)
  @GetMapping
  public Result<List<DataServiceView>> list() {
    return Result.success(reader.list().stream().map(viewFactory::view).toList());
  }

  @Operation(summary = "查询可发布的数据服务来源")
  @RequiresPermission(DataServicePermissionCode.PUBLISH)
  @GetMapping("/sources")
  public Result<SourcePage> sources(@RequestParam("sourceType") String sourceType,
      @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(value = "pageSize", defaultValue = "50") int pageSize,
      @RequestParam(value = "keyword", required = false) String keyword) {
    rejectManagedSource(sourceType);
    return Result.success(publicationReader.sources(sourceType, pageNo, pageSize, keyword));
  }

  @Operation(summary = "从已发布来源创建或更新数据服务")
  @RequiresPermission(DataServicePermissionCode.PUBLISH)
  @PostMapping("/publish")
  public Result<DataServiceView> publish(@RequestBody PublishDataServiceRequest request) {
    rejectManagedSource(request.sourceType());
    return Result.success(publisher.publish(new PublishRequest(request.sourceType(), request.sourceRef(), request.name(),
        request.path(), request.maxRows(), request.timeoutSeconds(), request.enabled(), request.description())));
  }

  @Operation(summary = "查询 API 服务详情")
  @RequiresPermission(DataServicePermissionCode.READ)
  @GetMapping("/{id}")
  public Result<DataServiceView> detail(@PathVariable("id") Long id) {
    return Result.success(viewFactory.view(reader.require(id)));
  }

  @Operation(summary = "更新 API 服务侧配置")
  @RequiresPermission(DataServicePermissionCode.MANAGE)
  @PutMapping("/{id}")
  public Result<DataServiceView> update(@PathVariable("id") Long id, @RequestBody UpdateDataServiceRequest input) {
    return Result.success(publisher.updateSettings(id, new PublicationSettings(input.name(), input.path(), input.maxRows(),
        input.timeoutSeconds(), input.enabled(), input.description())));
  }

  @Operation(summary = "按当前上游 Revision 重新发布数据服务")
  @RequiresPermission(DataServicePermissionCode.PUBLISH)
  @PostMapping("/{id}/republish")
  public Result<DataServiceView> republish(@PathVariable("id") Long id,
      @RequestBody(required = false) RepublishDataServiceRequest request) {
    rejectManagedService(id);
    RepublishDataServiceRequest values = request == null
        ? new RepublishDataServiceRequest(null, null, null, null, null, null) : request;
    return Result.success(publisher.republish(id, new PublicationSettings(values.name(), values.path(), values.maxRows(),
        values.timeoutSeconds(), values.enabled(), values.description())));
  }

  @Operation(summary = "删除 API 服务")
  @RequiresPermission(DataServicePermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    rejectManagedService(id);
    manager.delete(id);
    documentationManager.deleteForApi(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "启用或停用 API 服务")
  @RequiresPermission(DataServicePermissionCode.MANAGE)
  @PutMapping("/{id}/enabled")
  public Result<DataServiceView> setEnabled(@PathVariable("id") Long id, @RequestParam("enabled") boolean enabled) {
    rejectManagedService(id);
    return Result.success(viewFactory.view(manager.setEnabled(id, enabled)));
  }

  private void rejectManagedSource(String sourceType) {
    if (publicationReader.managesServiceDefinition(sourceType)) {
      throw new IllegalStateException("该发布来源由所属 authoring context 管理，请从来源工作台执行发布操作");
    }
  }

  private void rejectManagedService(Long apiId) {
    if (publisher.managesServiceDefinition(apiId)) {
      throw new IllegalStateException("该 API 由所属 authoring context 管理，请从来源工作台执行变更");
    }
  }

  public record PublishDataServiceRequest(String sourceType, String sourceRef, String name, String path,
      Integer maxRows, Integer timeoutSeconds, Boolean enabled, String description) {}
  public record UpdateDataServiceRequest(String name, String path, Integer maxRows, Integer timeoutSeconds,
      Boolean enabled, String description) {}
  public record RepublishDataServiceRequest(String name, String path, Integer maxRows, Integer timeoutSeconds,
      Boolean enabled, String description) {}
}
