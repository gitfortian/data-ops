package io.yak.ops.business.consumption.model;

import java.util.List;

/**
 * Result returned by the consumption discovery layer.
 *
 * This object intentionally contains projections only. It does not own
 * Dataset or Data Service lifecycle truth.
 */
public record ConsumptionQueryResult(
        List<DataProductView> products,
        long total
) {
}
