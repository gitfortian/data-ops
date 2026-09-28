package io.yak.ops.business.consumption.relationship;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据消费订阅")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/consumption/subscriptions")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class SubscriptionController {

  private final SubscriptionService service;

  @Operation(summary = "声明一个稳定的数据产品消费依赖")
  @PostMapping
  @RequiresPermission(AssetPermissionCode.UPDATE)
  public Result<Subscription> subscribe(@RequestBody SubscribeRequest request, Principal principal) {
    if (request == null) throw new IllegalArgumentException("subscription request is required");
    ConsumerRef consumerRef = new ConsumerRef(
        request.consumerType(),
        request.sourceDomain(),
        request.sourceIdentity(),
        request.displayHint());
    return Result.success(service.subscribe(
        ProductKey.parse(request.productKey()),
        consumerRef,
        request.consumptionMode(),
        actor(principal)));
  }

  @Operation(summary = "幂等取消消费订阅")
  @PostMapping("/{subscriptionId}/cancel")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  public Result<Subscription> cancel(
      @PathVariable Long subscriptionId,
      Principal principal) {
    return Result.success(service.cancel(subscriptionId, actor(principal)));
  }

  @Operation(summary = "暂停消费订阅")
  @PostMapping("/{subscriptionId}/suspend")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  public Result<Subscription> suspend(@PathVariable Long subscriptionId, Principal principal) {
    return Result.success(service.suspend(subscriptionId, actor(principal)));
  }

  @Operation(summary = "恢复已暂停的消费订阅")
  @PostMapping("/{subscriptionId}/resume")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  public Result<Subscription> resume(@PathVariable Long subscriptionId, Principal principal) {
    return Result.success(service.resume(subscriptionId, actor(principal)));
  }

  @Operation(summary = "查询项目内消费订阅")
  @GetMapping
  @RequiresPermission(AssetPermissionCode.READ)
  public Result<List<Subscription>> list(
      @RequestParam(required = false) String productKey,
      @RequestParam(required = false) ConsumerType consumerType,
      @RequestParam(required = false) String sourceDomain,
      @RequestParam(required = false) String sourceIdentity) {
    ProductKey key = productKey == null || productKey.isBlank() ? null : ProductKey.parse(productKey);
    ConsumerRef consumerRef = consumerFilter(consumerType, sourceDomain, sourceIdentity);
    return Result.success(service.list(key, consumerRef));
  }

  private static ConsumerRef consumerFilter(
      ConsumerType consumerType,
      String sourceDomain,
      String sourceIdentity) {
    boolean absent = consumerType == null
        && (sourceDomain == null || sourceDomain.isBlank())
        && (sourceIdentity == null || sourceIdentity.isBlank());
    if (absent) return null;
    if (consumerType == null || sourceDomain == null || sourceDomain.isBlank()
        || sourceIdentity == null || sourceIdentity.isBlank()) {
      throw new IllegalArgumentException(
          "consumerType, sourceDomain and sourceIdentity must be supplied together");
    }
    return new ConsumerRef(consumerType, sourceDomain, sourceIdentity, null);
  }

  private static String actor(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new IllegalArgumentException("authenticated principal is required");
    }
    return principal.getName();
  }

  public record SubscribeRequest(
      String productKey,
      ConsumerType consumerType,
      String sourceDomain,
      String sourceIdentity,
      String displayHint,
      ConsumptionMode consumptionMode) {}
}
