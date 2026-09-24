package io.yak.ops.business.consumption.product.domain;

/**
 * Supported data product domains exposed by the consumption layer.
 *
 * The consumption domain only projects business domain truth.
 */
public enum DataProductDomain {
    DATASET,
    DATA_SERVICE,
    METRIC,
    MASTER_DATA,
    MODEL,
    SEMANTIC,
    ANALYSIS
}
