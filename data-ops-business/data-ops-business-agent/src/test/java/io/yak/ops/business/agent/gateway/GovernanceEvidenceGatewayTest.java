package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.agent.domain.GovernanceEvidenceLedger;
import io.yak.ops.business.asset.api.AssetGovernanceQueryApi;
import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.business.quality.api.QualityEvidenceQueryApi;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class GovernanceEvidenceGatewayTest {
  private final AssetGovernanceQueryApi api = mock(AssetGovernanceQueryApi.class);
  private final GovernanceEvidenceGateway gateway = new GovernanceEvidenceGateway(provider(api), provider(null));

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    return provider;
  }

  private AssetSectionResult section(SectionStatus status) {
    return new AssetSectionResult(SectionType.TECHNICAL_METADATA, status, "METADATA",
        new SectionMapSummary(Map.of("columnCount", 1, "password", "top-secret", "executedSql", "SELECT secret",
            "columns", List.of(Map.of("name", "region", "dataType", "STRING", "jdbcUrl", "private-connection")))),
        "raw source error password=private", null, List.of(), List.of(), null, null);
  }

  @Test void metadataProjectionKeepsFactsAndExcludesArbitraryConfigSqlAndDiagnostics() {
    when(api.section(7, SectionType.TECHNICAL_METADATA)).thenReturn(section(SectionStatus.OK));
    String result = gateway.section(7, SectionType.TECHNICAL_METADATA, new GovernanceEvidenceLedger());
    assertTrue(result.contains("region"));
    assertTrue(result.contains("columnCount"));
    assertFalse(result.contains("top-secret"));
    assertFalse(result.contains("SELECT secret"));
    assertFalse(result.contains("private-connection"));
    assertFalse(result.contains("raw source error"));
  }

  @Test void deniedAndNotApplicableSectionsNeverExposeTheirPayload() {
    when(api.section(7, SectionType.TECHNICAL_METADATA)).thenReturn(section(SectionStatus.PERMISSION_DENIED));
    var ledger = new GovernanceEvidenceLedger();
    String denied = gateway.section(7, SectionType.TECHNICAL_METADATA, ledger);
    assertTrue(denied.contains("PERMISSION_DENIED"));
    assertFalse(denied.contains("region"));
    when(api.section(7, SectionType.TECHNICAL_METADATA)).thenReturn(section(SectionStatus.NOT_APPLICABLE));
    assertTrue(gateway.section(7, SectionType.TECHNICAL_METADATA, ledger).contains("NOT_APPLICABLE"));
  }

  @Test void sourceFailureDoesNotEraseOtherEvidenceAndRawErrorsNeverEscape() {
    var ledger = new GovernanceEvidenceLedger();
    ledger.register("ASSET", "7", "OK", null, "/data-asset/detail/7");
    when(api.section(7, SectionType.QUALITY)).thenThrow(new IllegalStateException("jdbc:secret"));
    String failed = gateway.section(7, SectionType.QUALITY, ledger);
    assertTrue(failed.contains("UNAVAILABLE"));
    assertFalse(failed.contains("jdbc:secret"));
    assertEquals(2, ledger.entries().size());
    when(api.require(7)).thenThrow(new ActionAccessDeniedException("data-asset:read"));
    assertTrue(gateway.asset(7, ledger).contains("PERMISSION_DENIED"));
  }
}
