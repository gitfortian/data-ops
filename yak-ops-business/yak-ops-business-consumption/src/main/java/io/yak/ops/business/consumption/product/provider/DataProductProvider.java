package io.yak.ops.business.consumption.product.provider;

import io.yak.ops.business.consumption.product.domain.DataProductDomain;
import io.yak.ops.business.consumption.product.identity.DataProductIdentity;
import io.yak.ops.business.consumption.product.model.DataProductView;

/**
 * Provider SPI for projecting domain-owned data products into consumption views.
 */
public interface DataProductProvider {

    boolean supports(DataProductDomain domain);

    DataProductView get(DataProductIdentity identity);
}
