package io.yak.ops.business.consumption.product.discovery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductService.NavigationResolution;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.SourceLifecycleState;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据消费中心")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/consumption")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.READ)
public class ProductDiscoveryController {

  private final ProductDiscoveryService discoveryService;
  private final CanonicalProductService canonicalService;

  @Operation(summary = "发现可消费 Dataset / Data Service")
  @GetMapping("/products")
  public Result<ProductDiscoveryResult> search(
      @RequestParam(required = false) ProductType productType,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String owner,
      @RequestParam(required = false) String visibility,
      @RequestParam(required = false) SourceLifecycleState lifecycle,
      @RequestParam(required = false) AvailabilityState availability) {
    ProductDiscoveryResult result = discoveryService.search(new ProductSearchCriteria(
        productType, normalize(keyword), normalize(owner), null, normalize(visibility), lifecycle, availability));
    return Result.success(result);
  }

  @Operation(summary = "读取唯一规范 Consumption Detail")
  @GetMapping("/products/{productKey:.+}")
  public Result<CanonicalProductDetail> get(@PathVariable String productKey) {
    return Result.success(canonicalService.detail(ProductKey.parse(productKey)));
  }

  @Operation(summary = "从来源 stable identity 进入规范 Consumption Detail")
  @GetMapping("/navigation/sources/{productType}/{sourceIdentity}")
  public Result<NavigationResolution> fromSource(
      @PathVariable ProductType productType,
      @PathVariable String sourceIdentity) {
    return Result.success(canonicalService.fromSource(productType, sourceIdentity));
  }

  @Operation(summary = "从 Asset 进入同一 ProductKey 的规范 Consumption Detail")
  @GetMapping("/navigation/assets/{assetId}")
  public Result<NavigationResolution> fromAsset(@PathVariable long assetId) {
    return Result.success(canonicalService.fromAsset(assetId));
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
