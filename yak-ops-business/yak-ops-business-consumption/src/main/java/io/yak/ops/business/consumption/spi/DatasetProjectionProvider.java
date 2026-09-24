package io.yak.ops.business.consumption.spi;

import io.yak.ops.business.consumption.model.DataProductView;
import io.yak.ops.business.consumption.model.ProductKey;

public interface DatasetProjectionProvider {

    boolean supports(ProductKey productKey);

    DataProductView project(ProductKey productKey);
}
