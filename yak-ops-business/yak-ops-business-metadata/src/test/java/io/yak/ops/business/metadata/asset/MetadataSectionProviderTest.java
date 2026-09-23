package io.yak.ops.business.metadata.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.api.MetadataQueryApi;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionResult;
import io.yak.ops.spi.section.SectionStatus;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetadataSectionProviderTest {
    private final MetadataQueryApi metadata = mock(MetadataQueryApi.class);
    private final MetadataSectionProvider provider = new MetadataSectionProvider(metadata);

    @Test
    void unsupportedAssetDoesNotQueryMetadata() {
        SectionResult result = (SectionResult) provider.query(
                new SectionContext("metric:1", "METRIC", "1"));
        assertEquals(SectionStatus.NOT_APPLICABLE, result.status());
        verifyNoInteractions(metadata);
    }

    @Test
    void missingTableIsConfirmedEmpty() {
        when(metadata.findPhysicalTable("table:one")).thenReturn(Optional.empty());
        SectionResult result = (SectionResult) provider.query(
                new SectionContext("table:one", "METADATA", "7"));
        assertEquals(SectionStatus.EMPTY, result.status());
        assertNull(result.summary());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void physicalTableKeepsSourceIdentityAndEvidence() {
        EntityDTO table = new EntityDTO(7L, "table",
                Map.of("dataSourceId", "ds", "databaseName", "db",
                        "tableName", "orders", "entityStatus", "ACTIVE"),
                Map.of(), Map.of());
        when(metadata.findPhysicalTable("table:one")).thenReturn(Optional.of(table));
        SectionResult result = (SectionResult) provider.query(
                new SectionContext("table:one", "METADATA", "7"));
        assertEquals(SectionStatus.OK, result.status());
        assertEquals("7", result.provenance().sourceId());
        assertEquals("7", result.actions().get(0).sourceId());
        assertEquals("orders",
                ((MetadataSectionProvider.TechnicalMetadataSummary) result.summary()).tableName());
    }

    @Test
    void mismatchedAssetAndMetadataIdsRequireReconciliation() {
        when(metadata.findPhysicalTable("table:one")).thenReturn(Optional.of(
                new EntityDTO(7L, "table", Map.of(), Map.of(), Map.of())));
        SectionResult result = (SectionResult) provider.query(
                new SectionContext("table:one", "METADATA", "8"));
        assertEquals(SectionStatus.UNAVAILABLE, result.status());
        assertTrue(result.evidence().isEmpty());
    }
}
