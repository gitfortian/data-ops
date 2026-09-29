package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.dataservice.documentation.ApiDocumentation;
import io.yak.ops.business.dataservice.documentation.DataServiceDocumentationManager;
import io.yak.ops.business.dataservice.documentation.DataServiceDocumentationReader;
import io.yak.ops.business.dataservice.documentation.DocumentationInput;
import io.yak.ops.business.dataservice.documentation.OpenApiRenderer;
import io.yak.ops.business.dataservice.publication.DataServicePublisher;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.dataservice.DataServicePermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据服务")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class DataServiceDocumentationController {
  private final DataServiceDocumentationReader reader;
  private final DataServiceDocumentationManager manager;
  private final OpenApiRenderer renderer;
  private final DataServicePublisher publisher;

  @Operation(summary = "查询 API 参数与响应文档")
  @RequiresPermission(DataServicePermissionCode.READ)
  @GetMapping("/{id}/documentation")
  public Result<ApiDocumentation> documentation(@PathVariable("id") Long id) {
    return Result.success(reader.get(id));
  }

  @Operation(summary = "保存 API 参数与响应文档")
  @RequiresPermission(DataServicePermissionCode.MANAGE)
  @PutMapping("/{id}/documentation")
  public Result<ApiDocumentation> saveDocumentation(
      @PathVariable("id") Long id,
      @RequestBody DocumentationInput input) {
    if (publisher.managesServiceDefinition(id)) {
      throw new IllegalStateException(
          "当前 API Contract 由数据开发 Data Service Node Revision 管理，请回到数据开发修改并重新发布");
    }
    return Result.success(manager.save(id, input));
  }

  @Operation(summary = "生成单个数据服务 OpenAPI 3 文档")
  @RequiresPermission(DataServicePermissionCode.READ)
  @GetMapping("/{id}/openapi")
  public Result<Map<String, Object>> openApi(@PathVariable("id") Long id) {
    return Result.success(renderer.render(reader.get(id)));
  }
}
