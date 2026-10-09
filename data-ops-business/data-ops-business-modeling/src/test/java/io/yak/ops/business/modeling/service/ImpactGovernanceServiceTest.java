package io.yak.ops.business.modeling.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImpactGovernanceServiceTest {

  @Test
  void defaultExtensionPointRetainsEmptyValidationForNullAndValidIds() {
    ImpactGovernanceService service = new ImpactGovernanceService() {};
    assertTrue(service.validateImpact(null, null).isEmpty());
    assertTrue(service.validateImpact("LOGICAL_MODEL", 42L).isEmpty());
  }
}
