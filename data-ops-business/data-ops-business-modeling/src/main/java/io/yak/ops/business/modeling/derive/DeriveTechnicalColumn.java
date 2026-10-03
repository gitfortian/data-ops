package io.yak.ops.business.modeling.derive;

/** A mandatory target-layer technical column owned by derivation rules. */
record DeriveTechnicalColumn(String name, String dataType, Integer length, String note) {}
