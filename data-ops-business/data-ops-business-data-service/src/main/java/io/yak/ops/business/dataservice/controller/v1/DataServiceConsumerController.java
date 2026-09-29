package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.dataservice.access.ApiKeyInput;
import io.yak.ops.business.dataservice.access.ApiKeyUpdate;
import io.yak.ops.business.dataservice.access.ApiKeyView;
import io.yak.ops.business.dataservice.access.ConsumerAccessInput;
import io.yak.ops.business.dataservice.access.ConsumerInput;
import io.yak.ops.business.dataservice.access.ConsumerIpAccessPolicyView;
import io.yak.ops.business.dataservice.access.ConsumerIpAccessRuleView;
import io.yak.ops.business.dataservice.access.ConsumerView;
import io.yak.ops.business.dataservice.access.CreatedApiKey;
import io.yak.ops.business.dataservice.access.DataServiceConsumerIpAccessManager;
import io.yak.ops.business.dataservice.access.DataServiceConsumerManager;
import io.yak.ops.business.dataservice.access.IpAccessRuleInput;
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

@Tag(name = "数据服务调用方")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service/consumers")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataServicePermissionCode.ACCESS)
public class DataServiceConsumerController {
  private final DataServiceConsumerManager consumerManager;
  private final DataServiceConsumerIpAccessManager ipAccessManager;

  @Operation(summary = "查询当前项目的数据服务调用方")
  @GetMapping
  public Result<List<ConsumerView>> list() {
    return Result.success(consumerManager.list());
  }

  @Operation(summary = "查询数据服务调用方")
  @GetMapping("/{consumerId}")
  public Result<ConsumerView> get(@PathVariable("consumerId") Long consumerId) {
    return Result.success(consumerManager.get(consumerId));
  }

  @Operation(summary = "创建数据服务调用方")
  @PostMapping
  public Result<ConsumerView> create(@RequestBody ConsumerInput input) {
    return Result.success(consumerManager.create(input));
  }

  @Operation(summary = "更新数据服务调用方")
  @PutMapping("/{consumerId}")
  public Result<ConsumerView> update(
      @PathVariable("consumerId") Long consumerId,
      @RequestBody ConsumerInput input) {
    return Result.success(consumerManager.update(consumerId, input));
  }

  @Operation(summary = "删除数据服务调用方")
  @DeleteMapping("/{consumerId}")
  public Result<Boolean> delete(@PathVariable("consumerId") Long consumerId) {
    consumerManager.delete(consumerId);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "配置调用方可访问的数据服务 API")
  @PutMapping("/{consumerId}/access")
  public Result<ConsumerView> updateAccess(
      @PathVariable("consumerId") Long consumerId,
      @RequestBody ConsumerAccessInput input) {
    return Result.success(consumerManager.updateAccess(consumerId, input));
  }

  @Operation(summary = "查询调用方 API Key")
  @GetMapping("/{consumerId}/keys")
  public Result<List<ApiKeyView>> listKeys(@PathVariable("consumerId") Long consumerId) {
    return Result.success(consumerManager.listKeys(consumerId));
  }

  @Operation(summary = "创建调用方 API Key（明文仅返回一次）")
  @PostMapping("/{consumerId}/keys")
  public Result<CreatedApiKey> createKey(
      @PathVariable("consumerId") Long consumerId,
      @RequestBody ApiKeyInput input) {
    return Result.success(consumerManager.createKey(consumerId, input));
  }

  @Operation(summary = "更新调用方 API Key")
  @PutMapping("/{consumerId}/keys/{keyId}")
  public Result<ApiKeyView> updateKey(
      @PathVariable("consumerId") Long consumerId,
      @PathVariable("keyId") Long keyId,
      @RequestBody ApiKeyUpdate input) {
    return Result.success(consumerManager.updateKey(consumerId, keyId, input));
  }

  @Operation(summary = "启用或停用调用方 API Key")
  @PutMapping("/{consumerId}/keys/{keyId}/enabled")
  public Result<ApiKeyView> setKeyEnabled(
      @PathVariable("consumerId") Long consumerId,
      @PathVariable("keyId") Long keyId,
      @RequestParam("enabled") boolean enabled) {
    return Result.success(consumerManager.setKeyEnabled(consumerId, keyId, enabled));
  }

  @Operation(summary = "轮换调用方 API Key（新明文仅返回一次）")
  @PostMapping("/{consumerId}/keys/{keyId}/rotate")
  public Result<CreatedApiKey> rotateKey(
      @PathVariable("consumerId") Long consumerId,
      @PathVariable("keyId") Long keyId) {
    return Result.success(consumerManager.rotateKey(consumerId, keyId));
  }

  @Operation(summary = "删除调用方 API Key")
  @DeleteMapping("/{consumerId}/keys/{keyId}")
  public Result<Boolean> deleteKey(
      @PathVariable("consumerId") Long consumerId,
      @PathVariable("keyId") Long keyId) {
    consumerManager.deleteKey(consumerId, keyId);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "查询调用方 IP 黑白名单")
  @GetMapping("/{consumerId}/ip-access")
  public Result<ConsumerIpAccessPolicyView> getIpAccess(
      @PathVariable("consumerId") Long consumerId) {
    return Result.success(ipAccessManager.getPolicy(consumerId));
  }

  @Operation(summary = "设置调用方 IP 访问模式")
  @PutMapping("/{consumerId}/ip-access/mode")
  public Result<ConsumerIpAccessPolicyView> setIpAccessMode(
      @PathVariable("consumerId") Long consumerId,
      @RequestParam("mode") String mode) {
    return Result.success(ipAccessManager.setMode(consumerId, mode));
  }

  @Operation(summary = "新增调用方 IP/CIDR 规则")
  @PostMapping("/{consumerId}/ip-access/rules")
  public Result<ConsumerIpAccessRuleView> createIpRule(
      @PathVariable("consumerId") Long consumerId,
      @RequestBody IpAccessRuleInput input) {
    return Result.success(ipAccessManager.createRule(consumerId, input));
  }

  @Operation(summary = "更新调用方 IP/CIDR 规则")
  @PutMapping("/{consumerId}/ip-access/rules/{ruleId}")
  public Result<ConsumerIpAccessRuleView> updateIpRule(
      @PathVariable("consumerId") Long consumerId,
      @PathVariable("ruleId") Long ruleId,
      @RequestBody IpAccessRuleInput input) {
    return Result.success(ipAccessManager.updateRule(consumerId, ruleId, input));
  }

  @Operation(summary = "删除调用方 IP/CIDR 规则")
  @DeleteMapping("/{consumerId}/ip-access/rules/{ruleId}")
  public Result<Boolean> deleteIpRule(
      @PathVariable("consumerId") Long consumerId,
      @PathVariable("ruleId") Long ruleId) {
    ipAccessManager.deleteRule(consumerId, ruleId);
    return Result.success(Boolean.TRUE);
  }
}
