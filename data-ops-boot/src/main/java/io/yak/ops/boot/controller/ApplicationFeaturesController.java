package io.yak.ops.boot.controller;

import io.yak.framework.common.Result;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only deployment switches; available even when an optional module is disabled. */
@RestController
@RequestMapping("/api/v1/system/features")
@ProjectScope(ProjectMigrationMode.LEGACY_GLOBAL)
public class ApplicationFeaturesController {

  private final boolean agentEnabled;

  public ApplicationFeaturesController(@Value("${yak.agent.enabled:false}") boolean agentEnabled) {
    this.agentEnabled = agentEnabled;
  }

  @GetMapping
  public Result<ApplicationFeatures> getFeatures() {
    return Result.success(new ApplicationFeatures(agentEnabled));
  }

  public record ApplicationFeatures(boolean agentEnabled) {}
}
