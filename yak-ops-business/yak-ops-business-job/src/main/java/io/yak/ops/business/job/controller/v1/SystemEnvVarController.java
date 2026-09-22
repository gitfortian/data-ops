package io.yak.ops.business.job.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.job.environment.SystemEnvVarService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** System environment variable management APIs for the settings page. */
@Tag(name = "系统环境变量接口")
@RestController
@RequestMapping("/api/v1/system/env-vars")
public class SystemEnvVarController {

  private final SystemEnvVarService service;

  public SystemEnvVarController(SystemEnvVarService service) {
    this.service = service;
  }

  @Operation(summary = "获取所有环境变量（含应用配置和系统默认）")
  @GetMapping
  public Result<List<EnvVarEntry>> list() {
    Map<String, String> appVars = service.getAll();
    Map<String, String> systemEnv = System.getenv();

    Map<String, EnvVarEntry> result = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : appVars.entrySet()) {
      result.put(entry.getKey(), new EnvVarEntry(entry.getKey(), entry.getValue(), "app"));
    }
    for (Map.Entry<String, String> entry : systemEnv.entrySet()) {
      result.putIfAbsent(entry.getKey(), new EnvVarEntry(entry.getKey(), entry.getValue(), "system"));
    }
    return Result.success(new ArrayList<>(result.values()));
  }

  @Operation(summary = "批量保存应用配置环境变量")
  @PutMapping
  public Result<Void> save(@RequestBody Map<String, String> variables) {
    service.batchSave(variables);
    return Result.success(null);
  }

  @Operation(summary = "删除指定应用配置环境变量")
  @DeleteMapping("/{key}")
  public Result<Void> delete(@PathVariable String key) {
    service.remove(key);
    return Result.success(null);
  }

  public record EnvVarEntry(String key, String value, String source) {}
}
