package io.yak.ops.business.consumption.product.discovery;

import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.DataProductProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Canonical provider registry. One owning-source projection provider is allowed per ProductType. */
@Component
public final class DataProductRegistry {

  private final Map<ProductType, DataProductProvider> providers;

  public DataProductRegistry(List<DataProductProvider> providers) {
    EnumMap<ProductType, DataProductProvider> indexed = new EnumMap<>(ProductType.class);
    for (DataProductProvider provider : providers) {
      DataProductProvider previous = indexed.putIfAbsent(provider.productType(), provider);
      if (previous != null) {
        throw new IllegalStateException("Duplicate DataProductProvider for " + provider.productType());
      }
    }
    this.providers = Map.copyOf(indexed);
  }

  public Optional<DataProductProvider> find(ProductType productType) {
    return Optional.ofNullable(providers.get(productType));
  }

  public DataProductProvider require(ProductType productType) {
    return find(productType)
        .orElseThrow(() -> new IllegalStateException("Product provider unavailable: " + productType));
  }

  public List<DataProductProvider> all() {
    return providers.values().stream().toList();
  }
}
