package io.yak.ops.business.consumption.product.application;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetAppService.AssetView;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import org.springframework.stereotype.Service;

/** Canonical detail and stable source/Asset ingress resolution. */
@Service
public class CanonicalConsumptionService {

  private final ConsumptionDiscoveryService discoveryService;
  private final ConsumptionNavigationFactory navigationFactory;
  private final AssetAppService assetAppService;

  public CanonicalConsumptionService(
      ConsumptionDiscoveryService discoveryService,
      ConsumptionNavigationFactory navigationFactory,
      AssetAppService assetAppService) {
    this.discoveryService = discoveryService;
    this.navigationFactory = navigationFactory;
    this.assetAppService = assetAppService;
  }

  public CanonicalConsumptionDetail detail(ProductKey key) {
    ProductLookupResult result = discoveryService.get(key);
    if (result.state() != ProductLookupState.FOUND) {
      return new CanonicalConsumptionDetail(result.state(), null, null, result.reason());
    }
    return new CanonicalConsumptionDetail(
        result.state(), result.product(), navigationFactory.forProduct(result.product()), null);
  }

  public NavigationResolution fromSource(ProductKey key) {
    CanonicalConsumptionDetail detail = detail(key);
    return detail.state() == ProductLookupState.FOUND
        ? NavigationResolution.found(key, detail.navigation().canonicalHref())
        : new NavigationResolution(detail.state().name(), key, null, detail.reason());
  }

  public NavigationResolution fromAsset(long assetId) {
    if (assetId <= 0L) {
      return NavigationResolution.notFound("Invalid assetId");
    }
    final AssetView asset;
    try {
      asset = assetAppService.get(assetId);
    } catch (RuntimeException exception) {
      return NavigationResolution.notFound(exception.getMessage());
    }
    ProductType type = productType(asset.sourceType());
    if (type == null) {
      return NavigationResolution.notApplicable(
          "Asset source type is not a Phase 4 Data Product: " + asset.sourceType());
    }
    ProductKey key = new ProductKey(type, asset.sourceId());
    return fromSource(key);
  }

  private ProductType productType(String sourceType) {
    if (sourceType == null) return null;
    return switch (sourceType.trim().toUpperCase()) {
      case "DATASET" -> ProductType.DATASET;
      case "DATA_SERVICE" -> ProductType.DATA_SERVICE;
      default -> null;
    };
  }

  public record NavigationResolution(
      String state,
      ProductKey productKey,
      String canonicalHref,
      String reason) {

    static NavigationResolution found(ProductKey key, String href) {
      return new NavigationResolution("FOUND", key, href, null);
    }

    static NavigationResolution notFound(String reason) {
      return new NavigationResolution("NOT_FOUND", null, null, reason);
    }

    static NavigationResolution notApplicable(String reason) {
      return new NavigationResolution("NOT_APPLICABLE", null, null, reason);
    }
  }
}
