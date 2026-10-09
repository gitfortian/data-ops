package io.yak.ops.business.modeling.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImpactRelationshipPersistenceAdapterTest {

  @Test
  void defaultExtensionPointRetainsEmptyRelationshipsForNullAndValidIds() {
    ImpactRelationshipPersistenceAdapter adapter =
        new ImpactRelationshipPersistenceAdapter() {};
    assertTrue(adapter.findRelatedObjects(null, null).isEmpty());
    assertTrue(adapter.findRelatedObjects("LOGICAL_ENTITY", 42L).isEmpty());
  }
}
