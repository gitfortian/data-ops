package io.yak.ops.business.consumption.product.discovery;

import io.yak.ops.business.asset.application.AssetAppService;
import io.yak.ops.business.asset.application.AssetAppService.AssetView;
import io.yak.ops.business.asset.application.AssetDiscoverService;
import io.yak.ops.business.asset.application.AssetDiscoverService.SectionView;
import io.yak.ops.business.consumption.product.discovery.CanonicalProductDetail.GovernanceEvidence;
import io.yak.ops.business.consumption.product.identity.DomainRef;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductSectionState;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Builds the unique canonical detail without taking ownership of source or governance truth. */
@Service
public class CanonicalProductService {

  private final ProductDiscoveryService discoveryService;
  private final AssetAppService assetAppService;
  private final AssetDiscoverService assetDiscoverService;

  @Autowired
  public CanonicalProductService(
      ProductDiscoveryService discoveryService, AssetAppService assetAppService,
      AssetDiscoverService assetDiscoverService) {
    this.discoveryService = discoveryService;
    this.assetAppService = assetAppService;
    this.assetDiscoverService = assetDiscoverService;
  }

  public CanonicalProductService(
      ProductDiscoveryService discoveryService, AssetAppService assetAppService) {
    this(discoveryService, assetAppService, null);
  }

  public CanonicalProductDetail detail(ProductKey key) {
    return detail(key, null);
  }

  public CanonicalProductDetail detail(ProductKey key, String operator) {
    ProductLookupResult lookup = discoveryService.get(key);
    if (lookup.state() != ProductLookupState.FOUND) {
      return new CanonicalProductDetail(lookup.state(), null, null, List.of(), lookup.reason());
    }
    DataProductView product = lookup.product();
    return new CanonicalProductDetail(
        lookup.state(), product, navigation(product), governanceEvidence(product, operator), null);
  }

  public NavigationResolution fromSource(ProductType productType, String sourceIdentity) {
    ProductKey key = new ProductKey(productType, sourceIdentity);
    CanonicalProductDetail detail = detail(key);
    return resolution(key, detail);
  }

  public NavigationResolution fromAsset(long assetId) {
    if (assetId <= 0L) return NavigationResolution.notFound("Invalid assetId");
    final AssetView asset;
    try {
      asset = assetAppService.get(assetId);
    } catch (RuntimeException exception) {
      return NavigationResolution.notFound(exception.getMessage());
    }
    ProductType productType = productType(asset.sourceType());
    if (productType == null) {
      return NavigationResolution.notApplicable(
          "Asset source type is not a Phase 4 Data Product: " + asset.sourceType());
    }
    ProductKey key = new ProductKey(productType, asset.sourceId());
    return resolution(key, detail(key));
  }

  private NavigationResolution resolution(ProductKey key, CanonicalProductDetail detail) {
    if (detail.state() == ProductLookupState.FOUND) {
      return NavigationResolution.found(key, detail.navigation().canonicalHref());
    }
    return new NavigationResolution(detail.state().name(), key, null, detail.reason());
  }

  private ProductNavigation navigation(DataProductView product) {
    String canonical = canonicalHref(product.productKey());
    String source = switch (product.productKey().productType()) {
      case DATASET -> "/dataset/" + encode(product.productKey().sourceIdentity());
      case DATA_SERVICE -> "/data-service/api/" + encode(product.productKey().sourceIdentity());
    };
    String asset = href(product.assetRef(), "ASSET", "/data-asset/detail/");
    String producer = producerHref(product.producerRef());
    return new ProductNavigation(canonical, source, asset, producer);
  }

  private String producerHref(DomainRef producerRef) {
    if (producerRef == null) return null;
    if ("DATA_DEVELOPMENT_NODE".equalsIgnoreCase(producerRef.domain())
        || "DATA_DEVELOPMENT_DATA_SERVICE".equalsIgnoreCase(producerRef.domain())) {
      return "/data-development?nodeId=" + encode(producerRef.identity());
    }
    return null;
  }

  private String href(DomainRef ref, String domain, String prefix) {
    if (ref == null || !domain.equalsIgnoreCase(ref.domain())) return null;
    return prefix + encode(ref.identity());
  }

  private List<GovernanceEvidence> governanceEvidence(DataProductView product, String operator) {
    List<GovernanceEvidence> evidence = new ArrayList<>();
    for (ProductSectionState section : product.sections()) {
      evidence.add(new GovernanceEvidence(
          section.sectionKey(), section.state(), section.ownerDomain(), section.observedAt(),
          Map.of(), section.reason()));
    }
    DomainRef assetRef = product.assetRef();
    if (assetRef == null || !"ASSET".equalsIgnoreCase(assetRef.domain())) return evidence;
    if (assetDiscoverService != null) {
      for (String key : List.of("quality", "security", "lineage")) {
        // Asset governance is not source ownership. Replace only the section actually read.
        evidence.removeIf(section -> key.equals(section.sectionKey()));
        evidence.add(assetSection(product, assetRef, key, operator));
      }
    }
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
          asset.updateTime() == null
              ? null
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

  private GovernanceEvidence assetSection(
      DataProductView product, DomainRef assetRef, String key, String operator) {
    String owner = key.toUpperCase(java.util.Locale.ROOT);
    try {
      SectionView view = assetDiscoverService.section(
          Long.parseLong(assetRef.identity()), owner, operator);
      ProviderEvidenceState state = switch (view.status()) {
        case OK -> ProviderEvidenceState.READY;
        case EMPTY -> ProviderEvidenceState.EMPTY;
        case UNAVAILABLE -> ProviderEvidenceState.UNAVAILABLE;
        case PERMISSION_DENIED -> ProviderEvidenceState.FORBIDDEN;
        case NOT_APPLICABLE -> ProviderEvidenceState.NOT_APPLICABLE;
      };
      Map<String, Object> facts = new LinkedHashMap<>();
      Instant observedAt = null;
      if (state == ProviderEvidenceState.READY || state == ProviderEvidenceState.EMPTY) {
        SectionSummary summary = null;
        if (view.data() instanceof SectionContract contract) {
          owner = contract.ownerDomain();
          summary = contract.summary();
          put(facts, "updatedAt", contract.updatedAt());
          put(facts, "provenance", contract.provenance());
          put(facts, "evidence", contract.evidence());
        } else if (view.data() instanceof SectionSummary typed) {
          summary = typed;
        }
        if (summary != null) summary.values().forEach((name, value) -> put(facts, name, value));
        else if (view.data() instanceof Map<?, ?> map) {
          map.forEach((name, value) -> put(facts, String.valueOf(name), value));
        } else if (view.data() != null) {
          facts.put("data", view.data());
        }
        facts.put("assetRef", assetRef);
        put(facts, "sourceRef", product.sourceRef());
        facts.put("evidenceScope", "INDEXED_SOURCE_ASSET");
        observedAt = Instant.now();
      }
      return new GovernanceEvidence(key, state, owner, observedAt, facts, view.note());
    } catch (RuntimeException unavailable) {
      return new GovernanceEvidence(key, ProviderEvidenceState.UNAVAILABLE, owner,
          null, Map.of(), "Governance section could not be read");
    }
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

  private String canonicalHref(ProductKey key) {
    return "/data-analysis/consumption/" + encode(key.value());
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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
