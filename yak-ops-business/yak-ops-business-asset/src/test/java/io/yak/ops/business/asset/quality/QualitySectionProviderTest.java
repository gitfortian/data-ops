package io.yak.ops.business.asset.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import org.junit.jupiter.api.Test;

class QualitySectionProviderTest {

  @Test
  void qualitySectionDeclaresQualityDomainAsTruthOwner() {
    QualitySectionProvider provider = new QualitySectionProvider();

    var contract = provider.query(new SectionContext("metadata:table:1", "METADATA", "1"));

    assertEquals(SectionType.QUALITY, provider.sectionType());
    assertEquals("QUALITY", contract.ownerDomain());
    assertEquals(SectionStatus.UNAVAILABLE, contract.status());
    assertNotNull(contract.summary());
    assertNotNull(contract.evidence());
    assertNotNull(contract.provenance());
    assertFalse(contract.capability().available());
  }
}
