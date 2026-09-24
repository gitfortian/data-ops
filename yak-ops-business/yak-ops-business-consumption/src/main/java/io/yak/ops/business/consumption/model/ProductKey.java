package io.yak.ops.business.consumption.model;

import java.util.Objects;

public record ProductKey(ProductType type, String sourceIdentity) {

    public ProductKey {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sourceIdentity, "sourceIdentity");
    }

    public String value() {
        return type.name().toLowerCase() + ":" + sourceIdentity;
    }
}
