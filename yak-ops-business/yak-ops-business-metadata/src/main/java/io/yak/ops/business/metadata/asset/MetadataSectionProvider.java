package io.yak.ops.business.metadata.asset;

import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.api.MetadataQueryApi;
import io.yak.ops.business.metadata.config.ConditionalOnMetadataPersistence;
import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionEvidence;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionResult;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionSummary;
import io.yak.ops.spi.section.SectionType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Physical table technical metadata comes from Metadata's project-scoped
 * read-side. No Asset snapshot or direct Metadata DAO access is used.
 */
@Component
@ConditionalOnMetadataPersistence
public class MetadataSectionProvider implements SectionProvider {

    private static final String OWNER = "Metadata";
    private static final String SOURCE_TYPE = "METADATA";
    private final MetadataQueryApi metadata;

    public MetadataSectionProvider(MetadataQueryApi metadata) {
        this.metadata = metadata;
    }

    @Override
    public SectionType sectionType() {
        return SectionType.TECHNICAL_METADATA;
    }

    @Override
    public boolean supports(SectionContext context) {
        return context != null && SOURCE_TYPE.equals(context.sourceType());
    }

    @Override
    public SectionContract query(SectionContext context) {
        Objects.requireNonNull(context, "context");
        if (!supports(context)) {
            return new SectionResult(sectionType(), SectionStatus.NOT_APPLICABLE, OWNER,
                    null, "当前资产类型不适用技术元数据分区", null, List.of(), List.of(),
                    null, new SectionCapability(false, true, "仅物理表适用"));
        }
        // MetadataQueryApi uses trusted CurrentProject; it does not take a caller project ID.
        EntityDTO entity = metadata.findPhysicalTable(context.assetKey()).orElse(null);
        if (entity == null) {
            return new SectionResult(sectionType(), SectionStatus.EMPTY, OWNER,
                    null, "当前项目中未发现已采集的物理表元数据", null, List.of(), List.of(),
                    null, new SectionCapability(true, true, null));
        }
        String id = String.valueOf(entity.id());
        if (context.sourceId() != null && !context.sourceId().equals(id)) {
            return new SectionResult(sectionType(), SectionStatus.UNAVAILABLE, OWNER,
                    null, "资产来源身份与元数据目录不一致，需对账", null, List.of(), List.of(),
                    null, new SectionCapability(true, false, "来源身份不一致"));
        }
        Instant observedAt = Instant.now();
        TechnicalMetadataSummary summary = new TechnicalMetadataSummary(
                entity.dataSourceId(), entity.databaseName(), entity.schemaName(),
                entity.tableName(), stringFact(entity, "entityStatus"), id);
        return new SectionResult(sectionType(), SectionStatus.OK, OWNER, summary, null,
                instantFact(entity, "sourceUpdatedAt"),
                List.of(new SectionAction("查看技术元数据", "/data-asset/catalog?view=entity", id)),
                List.of(new SectionEvidence(OWNER, id, observedAt)),
                new SectionProvenance(OWNER, id, observedAt),
                new SectionCapability(true, true, null));
    }

    private static String stringFact(EntityDTO entity, String key) {
        Object value = entity.facts().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Instant instantFact(EntityDTO entity, String key) {
        Object value = entity.facts().get(key);
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String raw) {
            try {
                return Instant.parse(raw);
            } catch (java.time.format.DateTimeParseException ignored) {
                // No invented timestamp if the source uses a different clock format.
            }
        }
        return null;
    }

    public record TechnicalMetadataSummary(
            String dataSourceId, String databaseName, String schemaName,
            String tableName, String entityStatus, String metadataEntityId)
            implements SectionSummary {
    }
}
