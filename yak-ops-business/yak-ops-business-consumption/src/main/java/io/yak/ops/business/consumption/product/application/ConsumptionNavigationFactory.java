package io.yak.ops.business.consumption.product.application;

import io.yak.ops.business.consumption.product.identity.DomainRef;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductType;
import org.springframework.stereotype.Component;

/** Builds navigation only from stable source/domain identities; display fields never participate. */
@Component
public class ConsumptionNavigationFactory {

  public ConsumptionNavigation forProduct(DataProductView product) {
    String key = product.productKey().value();
    return new ConsumptionNavigation(
        "/api/v1/consumption/products/" + key,
        sourceHref(product.productKey().productType(), product.productKey().sourceIdentity()),
        domainLink(product.assetRef()),
        domainLink(product.producerRef()));
  }

  public String canonicalHref(ProductType type, String sourceIdentity) {
    return "/api/v1/consumption/products/" + type.name() + ":" + sourceIdentity;
  }

  private String sourceHref(ProductType type, String identity) {
    return switch (type) {
      case DATASET -> "/api/v1/datasets/" + identity;
      case DATA_SERVICE -> "/api/v1/data-service/" + identity;
    };
  }

  private ConsumptionNavigation.DomainLink domainLink(DomainRef ref) {
    if (ref == null) return null;
    String href = switch (ref.domain().toUpperCase()) {
      case "ASSET" -> "/api/v1/assets/" + ref.identity();
      case "DATASET" -> "/api/v1/datasets/" + ref.identity();
      case "DATA_SERVICE" -> "/api/v1/data-service/" + ref.identity();
      default -> null;
    };
    return new ConsumptionNavigation.DomainLink(ref, href);
  }
}
