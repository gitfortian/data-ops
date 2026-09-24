package io.yak.ops.business.consumption.product.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.consumption.product.application.CanonicalConsumptionDetail;
import io.yak.ops.business.consumption.product.application.CanonicalConsumptionService;
import io.yak.ops.business.consumption.product.application.CanonicalConsumptionService.NavigationResolution;
import io.yak.ops.business.consumption.product.application.ConsumptionDiscoveryResult;
import io.yak.ops.business.consumption.product.application.ConsumptionDiscoveryService;
import io.yak.ops.business.consumption.product.application.ConsumptionNavigationFactory;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.SourceLifecycleState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Search, Hub, source and Asset all converge on the same ProductKey detail contract. */
@Tag(name = "数据消费中心")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/consumption")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.READ)
public class ConsumptionController {

  private final ConsumptionDiscoveryService discoveryService;
  private final CanonicalConsumptionService canonicalService;
  private final ConsumptionNavigationFactory navigationFactory;

  @Operation(summary = "发现可消费 Dataset / Data Service")
  @GetMapping("/products")
  public Result<DiscoveryResponse> discover(
      @RequestParam(value = "productType", required = false) ProductType productType,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "owner", required = false) String owner,
      @RequestParam(value = "projectId", required = false) Long projectId,
      @RequestParam(value = "visibility", required = false) String visibility,
      @RequestParam(value = "lifecycle", required = false) SourceLifecycleState lifecycle,
      @RequestParam(value = "availability", required = false) AvailabilityState availability) {
    ProductSearchCriteria criteria = new ProductSearchCriteria(
        productType, keyword, owner, projectId, visibility, lifecycle, availability);
    ConsumptionDiscoveryResult result = discoveryService.discover(criteria);
    List<DiscoveryItem> items = result.products().stream()
        .map(product -> new DiscoveryItem(
            product, navigationFactory.forProduct(product).canonicalHref()))
        .toList();
    return Result.success(new DiscoveryResponse(
        items, result.total(), result.partial(), result.confirmedEmpty(), result.providers()));
  }

  @Operation(summary = "读取唯一规范 Consumption Detail")
  @GetMapping("/products/{productKey:.+}")
  public Result<CanonicalConsumptionDetail> detail(@PathVariable String productKey) {
    return Result.success(canonicalService.detail(ProductKey.parse(productKey)));
  }

  @Operation(summary = "从来源稳定 identity 进入规范 Consumption Detail")
  @GetMapping("/navigation/sources/{productType}/{sourceIdentity}")
  public Result<NavigationResolution> fromSource(
      @PathVariable ProductType productType,
      @PathVariable String sourceIdentity) {
    return Result.success(canonicalService.fromSource(new ProductKey(productType, sourceIdentity)));
  }

  @Operation(summary = "从 Asset 进入同一 ProductKey 的规范 Consumption Detail")
  @GetMapping("/navigation/assets/{assetId}")
  public Result<NavigationResolution> fromAsset(@PathVariable long assetId) {
    return Result.success(canonicalService.fromAsset(assetId));
  }

  public record DiscoveryItem(DataProductView product, String canonicalHref) {}

  public record DiscoveryResponse(
      List<DiscoveryItem> items,
      long total,
      boolean partial,
      boolean confirmedEmpty,
      List<ConsumptionDiscoveryResult.ProviderObservation> providers) {}
}
