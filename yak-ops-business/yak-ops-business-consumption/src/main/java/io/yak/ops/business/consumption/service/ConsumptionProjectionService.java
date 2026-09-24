package io.yak.ops.business.consumption.service;

import io.yak.ops.business.consumption.model.DataProductView;
import io.yak.ops.business.consumption.model.ProductKey;
import io.yak.ops.business.consumption.spi.DataServiceProjectionProvider;
import io.yak.ops.business.consumption.spi.DatasetProjectionProvider;

import java.util.List;

/**
 * Coordinates governed product projections.
 *
 * Providers remain owners of source-domain truth. The consumption domain only
 * assembles consumer-facing projections.
 */
public class ConsumptionProjectionService {

    private final List<DatasetProjectionProvider> datasetProviders;
    private final List<DataServiceProjectionProvider> dataServiceProviders;

    public ConsumptionProjectionService(
            List<DatasetProjectionProvider> datasetProviders,
            List<DataServiceProjectionProvider> dataServiceProviders
    ) {
        this.datasetProviders = datasetProviders;
        this.dataServiceProviders = dataServiceProviders;
    }

    public DataProductView get(ProductKey productKey) {
        return switch (productKey.type()) {
            case DATASET -> datasetProviders.stream()
                    .filter(provider -> provider.supports(productKey))
                    .findFirst()
                    .map(provider -> provider.project(productKey))
                    .orElse(null);
            case DATA_SERVICE -> dataServiceProviders.stream()
                    .filter(provider -> provider.supports(productKey))
                    .findFirst()
                    .map(provider -> provider.project(productKey))
                    .orElse(null);
        };
    }
}
