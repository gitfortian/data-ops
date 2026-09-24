package io.yak.ops.business.consumption.product.application;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.DataProductProvider;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchResult;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/** Canonical discovery/detail application service over all source-owned providers. */
@Service
public class ConsumptionDiscoveryService {

  private final DataProductRegistry registry;

  public ConsumptionDiscoveryService(DataProductRegistry registry) {
    this.registry = registry;
  }

  public ProductLookupResult get(ProductKey key) {
    return registry.find(key.productType())
        .map(provider -> safeGet(provider, key))
        .orElseGet(() -> ProductLookupResult.unavailable(
            "Provider unavailable for " + key.productType()));
  }

  public ConsumptionDiscoveryResult discover(ProductSearchCriteria criteria) {
    List<ProductType> requested = criteria != null && criteria.productType() != null
        ? List.of(criteria.productType())
        : Arrays.asList(ProductType.values());
    List<DataProductView> products = new ArrayList<>();
    List<ConsumptionDiscoveryResult.ProviderObservation> observations = new ArrayList<>();
    long total = 0L;
    boolean partial = false;

    for (ProductType type : requested) {
      DataProductProvider provider = registry.find(type).orElse(null);
      if (provider == null) {
        partial = true;
        observations.add(new ConsumptionDiscoveryResult.ProviderObservation(
            type, ProductSearchState.UNAVAILABLE, 0L, "Provider is not registered"));
        continue;
      }
      ProductSearchResult result = safeSearch(provider, criteria);
      observations.add(new ConsumptionDiscoveryResult.ProviderObservation(
          type, result.state(), result.total(), result.reason()));
      if (result.state() == ProductSearchState.READY) {
        products.addAll(result.products());
        total += result.total();
      } else {
        partial = true;
      }
    }
    return new ConsumptionDiscoveryResult(products, total, partial, observations);
  }

  private ProductLookupResult safeGet(DataProductProvider provider, ProductKey key) {
    try {
      ProductLookupResult result = provider.get(key);
      return result == null ? ProductLookupResult.unavailable("Provider returned null lookup result") : result;
    } catch (RuntimeException exception) {
      return ProductLookupResult.unavailable(message(exception));
    }
  }

  private ProductSearchResult safeSearch(DataProductProvider provider, ProductSearchCriteria criteria) {
    try {
      ProductSearchResult result = provider.search(criteria);
      return result == null ? ProductSearchResult.unavailable("Provider returned null search result") : result;
    } catch (RuntimeException exception) {
      return ProductSearchResult.unavailable(message(exception));
    }
  }

  private String message(RuntimeException exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? exception.getClass().getSimpleName() : exception.getMessage();
  }
}
