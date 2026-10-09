package io.yak.ops.business.modeling.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Preserve each standalone impact extension point's existing no-op contract. */
class ImpactRelationshipAdapterTest {

  @Test
  void allNoopAdaptersReturnEmptyForNullAndValidIds() {
    List<ImpactRelationshipAdapter> adapters = List.of(
        new DefaultImpactRelationshipAdapter(),
        new PersistenceBackedImpactRelationshipAdapter(),
        new LogicalEntityImpactRelationshipAdapter(),
        new LogicalModelImpactRelationshipAdapter(),
        new MappingImpactRelationshipAdapter());

    for (ImpactRelationshipAdapter adapter : adapters) {
      assertTrue(adapter.findImpacts(null, null).isEmpty(), adapter.getClass().getName());
      assertTrue(adapter.findImpacts("LOGICAL_MODEL", 42L).isEmpty(),
          adapter.getClass().getName());
    }
  }
}
