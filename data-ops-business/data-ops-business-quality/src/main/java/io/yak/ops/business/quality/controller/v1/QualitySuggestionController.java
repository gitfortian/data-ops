package io.yak.ops.business.quality.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.api.QualitySuggestionQueryApi;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.monitor.QualitySuggestionQueryAdapter;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Revalidates a candidate at adoption time, without saving or executing a monitor. */
@RestController
@ConditionalOnQualityEnabled
@ProjectScope
@RequiresPermission(QualityPermissionCode.MONITOR_READ)
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-quality/monitor")
public class QualitySuggestionController {
  private final QualitySuggestionQueryAdapter suggestions;

  public record ValidationRequest(
      @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String expectedDefinition,
      @NotEmpty @Size(max = 5) List<QualitySuggestionQueryApi.Candidate> rules) {}

  @PostMapping("/{id}/suggestions/validate")
  public Result<List<QualitySuggestionQueryApi.Candidate>> validate(@PathVariable long id,
      @Valid @RequestBody ValidationRequest request) {
    return Result.success(suggestions.validate(id, request.expectedDefinition(), request.rules()));
  }
}
