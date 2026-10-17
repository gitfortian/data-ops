package io.yak.ops.business.modeling.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.derive.DwsGrainDesignService;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** DWS grain contract; this controller never creates SQL or runs the warehouse. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models/{modelId}/dws-grain")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class DwsGrainDesignController {
  private final DwsGrainDesignService service;
  private final CurrentUserProvider currentUsers;

  @GetMapping
  public Result<DwsGrainDesignService.Saved> inspect(@PathVariable long modelId) {
    return Result.success(service.inspect(modelId).orElse(null));
  }

  @GetMapping("/upstream-candidates")
  public Result<java.util.List<DwsGrainDesignService.UpstreamCandidate>> candidates(
      @PathVariable long modelId) {
    return Result.success(service.candidates(modelId));
  }

  @PutMapping
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<DwsGrainDesignService.Saved> save(
      @PathVariable long modelId, @Valid @RequestBody DwsGrainDesignService.Request request,
      HttpServletRequest httpRequest) {
    return Result.success(service.save(
        modelId, request, currentUsers.getCurrentUser(httpRequest)));
  }
}
