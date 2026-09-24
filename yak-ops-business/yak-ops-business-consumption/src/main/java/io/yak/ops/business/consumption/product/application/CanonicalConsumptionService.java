package io.yak.ops.business.consumption.product.application;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetAppService.AssetView;
import io.yak.ops.business.consumption.product.application.CanonicalConsumptionDetail.GovernanceEvidence;
import io.yak.ops.business.consumption.product.identity.DomainRef;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductSectionState;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
      return new CanonicalConsumptionDetail(result.state(), null, null, List.of(), result.reason());
    }
    DataProductView product = result.product();
    return new CanonicalConsumptionDetail(
        result.state(), product, navigationFactory.forProduct(product), evidence(product), null);
  }

  public NavigationResolution fromSource(ProductKey key) {
    CanonicalConsumptionDetail detail = detail(key);
    return detail.state() == ProductLookupState.FOUND
        ? NavigationResolution.found(key, detail.navigation().canonicalHref())
        : new NavigationResolution(detail.state().name(), key, null, detail.reason());
  }

  public NavigationResolution fromAsset(long assetId) {
    if (assetId <= 0L) return NavigationResolution.notFound("Invalid assetId");
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
    return fromSource(new ProductKey(type, asset.sourceId()));
  }

  private List<GovernanceEvidence> evidence(DataProductView product) {
    List<GovernanceEvidence> evidence = new ArrayList<>();
    for (ProductSectionState section : product.sections()) {
      evidence.add(new GovernanceEvidence(
          section.sectionKey(), section.state(), section.ownerDomain(), section.observedAt(),
          Map.of(), section.reason()));
    }
    DomainRef assetRef = product.assetRef();
    if (assetRef == null || !"ASSET".equalsIgnoreCase(assetRef.domain())) return evidence;
    try {
      AssetView asset = assetAppService.get(Long.parseLong(assetRef.identity()));
      Map<String, Object> facts = new LinkedHashMap<>();
      put(facts, "status", asset.status());
      put(facts, "owner", asset.owner());
      put(facts, "securityLevel", asset.securityLevelCode());
      put(facts, "healthScore", asset.healthScore());
      put(facts, "healthGrade", asset.healthGrade());
      evidence.add(new GovernanceEvidence(
          "asset-governance",
          ProviderEvidenceState.READY,
          "ASSET",
          asset.updateTime() == null ? null
              : asset.updateTime().atZone(ZoneId.systemDefault()).toInstant(),
          facts,
          null));
    } catch (RuntimeException exception) {
      evidence.add(new GovernanceEvidence(
          "asset-governance", ProviderEvidenceState.UNAVAILABLE, "ASSET", null, Map.of(),
          exception.getMessage()));
    }
    return evidence;
  }

  private void put(Map<String, Object> facts, String key, Object value) {
    if (value != null) facts.put(key, value);
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
