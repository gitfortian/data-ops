package io.yak.ops.business.consumption.model;

import java.util.List;

public record DataProductView(
        ProductKey productKey,
        ProductType productType,
        String owner,
        String project,
        String lifecycle,
        String availability,
        List<String> consumptionActions
) {
}
