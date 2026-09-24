package io.yak.ops.business.consumption.product.application;

import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.DataProductProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Single registry for canonical Data Product providers. Duplicate ownership fails fast. */
@Component
public class DataProductRegistry {

  private final Map<ProductType, DataProductProvider> providers;

  public DataProductRegistry(List<DataProductProvider> providerList) {
    EnumMap<ProductType, DataProductProvider> indexed = new EnumMap<>(ProductType.class);
    for (DataProductProvider provider : providerList == null ? List.<DataProductProvider>of() : providerList) {
      DataProductProvider previous = indexed.putIfAbsent(provider.productType(), provider);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate DataProductProvider for " + provider.productType() + ": "
                + previous.getClass().getName() + " and " + provider.getClass().getName());
      }
    }
    this.providers = Map.copyOf(indexed);
  }

  public Optional<DataProductProvider> find(ProductType type) {
    return Optional.ofNullable(providers.get(type));
  }

  public DataProductProvider require(ProductType type) {
    return find(type).orElseThrow(
        () -> new IllegalStateException("DataProductProvider unavailable for " + type));
  }

  public Set<ProductType> registeredTypes() {
    return providers.keySet();
  }
}
