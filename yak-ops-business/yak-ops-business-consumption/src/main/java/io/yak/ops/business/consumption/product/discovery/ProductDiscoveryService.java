package io.yak.ops.business.consumption.product.discovery;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.DataProductProvider;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import io.yak.ops.business.consumption.product.provider.ProductSearchResult;
import io.yak.ops.business.consumption.product.provider.ProductSearchState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Application service for canonical consumption discovery and detail lookup. */
@Service
@RequiredArgsConstructor
public class ProductDiscoveryService {

  private final DataProductRegistry registry;

  public ProductLookupResult get(ProductKey key) {
    return registry.find(key.productType())
        .map(provider -> provider.get(key))
        .orElseGet(() -> ProductLookupResult.unavailable("product provider unavailable"));
  }

  public ProductDiscoveryResult search(ProductSearchCriteria criteria) {
    List<DataProductProvider> providers = criteria.productType() == null
        ? registry.all()
        : registry.find(criteria.productType()).map(List::of).orElseGet(List::of);

    List<DataProductView> products = new ArrayList<>();
    long total = 0L;
    Map<ProductType, ProductSearchState> states = new EnumMap<>(ProductType.class);
    Map<ProductType, String> reasons = new EnumMap<>(ProductType.class);

    if (criteria.productType() != null && providers.isEmpty()) {
      states.put(criteria.productType(), ProductSearchState.UNAVAILABLE);
      reasons.put(criteria.productType(), "product provider unavailable");
    }

    for (DataProductProvider provider : providers) {
      ProductSearchResult result = provider.search(criteria);
      states.put(provider.productType(), result.state());
      if (result.reason() != null && !result.reason().isBlank()) {
        reasons.put(provider.productType(), result.reason());
      }
      if (result.state() == ProductSearchState.READY) {
        products.addAll(result.products());
        total += result.total();
      }
    }

    products.sort(Comparator
        .comparing(DataProductView::name, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(view -> view.productKey().value()));
    return new ProductDiscoveryResult(products, total, states, reasons);
  }
}
