package io.yak.ops.business.consumption.product.discovery;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.SourceLifecycleState;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/consumption/products")
@RequiredArgsConstructor
public class ProductDiscoveryController {

  private final ProductDiscoveryService service;

  @GetMapping
  public ProductDiscoveryResult search(
      @RequestParam(required = false) ProductType productType,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String owner,
      @RequestParam(required = false) String visibility,
      @RequestParam(required = false) SourceLifecycleState lifecycle,
      @RequestParam(required = false) AvailabilityState availability) {
    return service.search(new ProductSearchCriteria(
        productType, normalize(keyword), normalize(owner), null, normalize(visibility), lifecycle, availability));
  }

  @GetMapping("/{productKey}")
  public ProductLookupResult get(@PathVariable String productKey) {
    return service.get(ProductKey.parse(productKey));
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
