package io.yak.ops.business.asset.controller.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.asset.application.AssetAppService.AssetView;
import io.yak.ops.business.asset.application.AssetDiscoverService.SectionView;
import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssetControllerSectionTest {

  @Test
  void deniedSectionDoesNotExposeSummaryOrSourceIdentity() {
    AssetSectionResult result = AssetController.toSectionResult(
        SectionType.SECURITY,
        new SectionView(io.yak.ops.spi.section.SectionStatus.PERMISSION_DENIED, "当前用户无权查看该治理证据", null),
        asset());

    assertEquals("PERMISSION_DENIED", result.status().name());
    assertTrue(((SectionMapSummary) result.summary()).values().isEmpty());
    assertTrue(result.evidence().isEmpty());
    assertNull(result.provenance());
    assertTrue(result.actions().isEmpty());
    assertFalse(result.capability().available());
    assertNotNull(result.reason());
  }

  @Test
  void notApplicableSectionDoesNotClaimAReadSource() {
    AssetSectionResult result = AssetController.toSectionResult(
        SectionType.TECHNICAL_METADATA,
        new SectionView(io.yak.ops.spi.section.SectionStatus.NOT_APPLICABLE, "当前资产类型不适用", null),
        asset());

    assertEquals("NOT_APPLICABLE", result.status().name());
    assertNull(result.provenance());
    assertTrue(result.evidence().isEmpty());
    assertFalse(result.capability().applicable());
    assertEquals("当前资产类型不适用", result.reason());
  }

  @Test
  void confirmedEmptySectionRetainsQueryProvenanceAndReason() {
    AssetSectionResult result = AssetController.toSectionResult(
        SectionType.QUALITY,
        new SectionView(io.yak.ops.spi.section.SectionStatus.EMPTY, "该物理表尚未纳入质量监控", Map.of()),
        physicalTableAsset());

    assertEquals("EMPTY", result.status().name());
    assertNotNull(result.provenance());
    assertEquals("QUALITY", result.provenance().sourceDomain());
    assertEquals("table:3:crm_db..crm_customer_address", result.provenance().sourceId());
    assertEquals("该物理表尚未纳入质量监控", result.reason());
    assertTrue(result.capability().applicable());
    assertTrue(result.capability().available());
  }

  private static AssetView asset() {
    return new AssetView(
        8L, "modeling:model:49", "MODEL", "49", "TABLE", "用户信息表", null,
        null, null, null, "root", "PENDING", null, null, null, null, null,
        null, null, null, null, null, null, null, null);
  }

  private static AssetView physicalTableAsset() {
    return new AssetView(
        391L, "table:3:crm_db..crm_customer_address", "METADATA", "391", "TABLE", "用户信息表", null,
        null, null, null, "root", "PENDING", null, null, null, null, null,
        null, null, null, null, null, null, null, null);
  }
}
