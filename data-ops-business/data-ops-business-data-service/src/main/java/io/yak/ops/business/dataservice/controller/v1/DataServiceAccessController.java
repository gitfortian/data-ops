package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.dataservice.access.ApiKeyInput;
import io.yak.ops.business.dataservice.access.ApiKeyUpdate;
import io.yak.ops.business.dataservice.access.ApiKeyView;
import io.yak.ops.business.dataservice.access.CreatedApiKey;
import io.yak.ops.business.dataservice.access.DataServiceApiKeyManager;
import io.yak.ops.business.dataservice.access.DataServiceIpAccessManager;
import io.yak.ops.business.dataservice.access.IpAccessPolicyView;
import io.yak.ops.business.dataservice.access.IpAccessRuleInput;
import io.yak.ops.business.dataservice.access.IpAccessRuleView;
import io.yak.ops.business.dataservice.query.DataServiceAccessOverviewItem;
import io.yak.ops.business.dataservice.query.DataServiceAccessOverviewReader;
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

@Tag(name = "数据服务")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataServicePermissionCode.ACCESS)
public class DataServiceAccessController {
  private final DataServiceApiKeyManager apiKeyManager;
  private final DataServiceIpAccessManager ipAccessManager;
  private final DataServiceAccessOverviewReader accessOverviewReader;

  @Operation(summary = "查询当前项目的数据服务访问控制概览")
  @GetMapping("/access-overview")
  public Result<List<DataServiceAccessOverviewItem>> accessOverview() {
    return Result.success(accessOverviewReader.list());
  }

  @Operation(summary = "设置数据服务访问控制模式")
  @PutMapping("/{id}/auth-mode")
  public Result<String> setAuthMode(@PathVariable("id") Long id, @RequestParam("mode") String mode) {
    return Result.success(apiKeyManager.setAuthMode(id, mode).name());
  }

  @Operation(summary = "查询数据服务 API Key")
  @GetMapping("/{id}/keys")
  public Result<List<ApiKeyView>> listKeys(@PathVariable("id") Long id) {
    return Result.success(apiKeyManager.listKeys(id));
  }

  @Operation(summary = "创建数据服务 API Key（明文仅返回一次）")
  @PostMapping("/{id}/keys")
  public Result<CreatedApiKey> createKey(@PathVariable("id") Long id, @RequestBody ApiKeyInput input) {
    return Result.success(apiKeyManager.createKey(id, input));
  }

  @Operation(summary = "更新数据服务 API Key 配置")
  @PutMapping("/{id}/keys/{keyId}")
  public Result<ApiKeyView> updateKey(
      @PathVariable("id") Long id,
      @PathVariable("keyId") Long keyId,
      @RequestBody ApiKeyUpdate input) {
    return Result.success(apiKeyManager.updateKey(id, keyId, input));
  }

  @Operation(summary = "启用或停用数据服务 API Key")
  @PutMapping("/{id}/keys/{keyId}/enabled")
  public Result<ApiKeyView> setKeyEnabled(
      @PathVariable("id") Long id,
      @PathVariable("keyId") Long keyId,
      @RequestParam("enabled") boolean enabled) {
    return Result.success(apiKeyManager.setKeyEnabled(id, keyId, enabled));
  }

  @Operation(summary = "轮换数据服务 API Key（新明文仅返回一次）")
  @PostMapping("/{id}/keys/{keyId}/rotate")
  public Result<CreatedApiKey> rotateKey(
      @PathVariable("id") Long id,
      @PathVariable("keyId") Long keyId) {
    return Result.success(apiKeyManager.rotateKey(id, keyId));
  }

  @Operation(summary = "删除数据服务 API Key")
  @DeleteMapping("/{id}/keys/{keyId}")
  public Result<Boolean> deleteKey(
      @PathVariable("id") Long id,
      @PathVariable("keyId") Long keyId) {
    apiKeyManager.deleteKey(id, keyId);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "查询数据服务 IP 黑白名单策略")
  @GetMapping("/{id}/ip-access")
  public Result<IpAccessPolicyView> getIpAccess(@PathVariable("id") Long id) {
    return Result.success(ipAccessManager.getPolicy(id));
  }

  @Operation(summary = "设置数据服务 IP 访问模式")
  @PutMapping("/{id}/ip-access/mode")
  public Result<IpAccessPolicyView> setIpAccessMode(
      @PathVariable("id") Long id, @RequestParam("mode") String mode) {
    return Result.success(ipAccessManager.setMode(id, mode));
  }

  @Operation(summary = "新增数据服务 IP/CIDR 规则")
  @PostMapping("/{id}/ip-access/rules")
  public Result<IpAccessRuleView> createIpAccessRule(
      @PathVariable("id") Long id, @RequestBody IpAccessRuleInput input) {
    return Result.success(ipAccessManager.createRule(id, input));
  }

  @Operation(summary = "更新数据服务 IP/CIDR 规则")
  @PutMapping("/{id}/ip-access/rules/{ruleId}")
  public Result<IpAccessRuleView> updateIpAccessRule(
      @PathVariable("id") Long id,
      @PathVariable("ruleId") Long ruleId,
      @RequestBody IpAccessRuleInput input) {
    return Result.success(ipAccessManager.updateRule(id, ruleId, input));
  }

  @Operation(summary = "删除数据服务 IP/CIDR 规则")
  @DeleteMapping("/{id}/ip-access/rules/{ruleId}")
  public Result<Boolean> deleteIpAccessRule(
      @PathVariable("id") Long id, @PathVariable("ruleId") Long ruleId) {
    ipAccessManager.deleteRule(id, ruleId);
    return Result.success(Boolean.TRUE);
  }
}
