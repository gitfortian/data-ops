package io.yak.ops.business.consumption.relationship.source;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.relationship.ConsumerRef;
import io.yak.ops.business.consumption.relationship.ConsumerType;
import io.yak.ops.business.consumption.relationship.ConsumptionMode;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceService;
import io.yak.ops.business.consumption.relationship.UsageNormalizationResult;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Converts source-owned Data Service invocation audit into normalized successful usage evidence. */
@Component
@RequiredArgsConstructor
public class DataServiceUsageEvidenceNormalizer {

  static final String PROVIDER = "DATA_SERVICE_INVOCATION";
  private final UsageEvidenceService usageEvidenceService;

  public UsageNormalizationResult normalize(InvocationRecord record) {
    String ref = evidenceRef(record);
    if (record == null) {
      return UsageNormalizationResult.gap(null, "Data Service invocation evidence is missing");
    }
    if (!record.success()) {
      return UsageNormalizationResult.ignored(ref, "Failed invocation is audit evidence, not usage");
    }
    String gap = attributionGap(record);
    if (gap != null) {
      return UsageNormalizationResult.gap(ref, gap);
    }
    try {
      UsageEvidence evidence = usageEvidenceService.normalize(
          new UsageEvidenceService.UsageEvidenceCommand(
              record.projectId(),
              new ProductKey(ProductType.DATA_SERVICE, record.apiId().toString()),
              new SourceVersionRef(
                  record.sourceRevisionId().toString(),
                  record.sourceRevisionNo() == null ? null : "r" + record.sourceRevisionNo()),
              new ConsumerRef(
                  ConsumerType.DATA_SERVICE,
                  "DATA_SERVICE_CONSUMER",
                  record.consumerId().toString(),
                  record.apiKeyName()),
              record.createTime(),
              ConsumptionMode.API_INVOKE,
              PROVIDER,
              ref,
              PROVIDER + ":" + record.id()));
      return UsageNormalizationResult.normalized(evidence);
    } catch (RuntimeException failure) {
      return UsageNormalizationResult.unavailable(
          ref,
          failure.getMessage() == null ? "Usage normalization failed" : failure.getMessage());
    }
  }

  private String attributionGap(InvocationRecord record) {
    if (record.id() == null) return "Invocation audit has no stable evidence id";
    if (record.projectId() == null || record.projectId() <= 0L) return "Invocation audit has no Project Space";
    if (record.apiId() == null || record.apiId() <= 0L) return "Invocation audit has no Data Service identity";
    if (record.consumerId() == null || record.consumerId() <= 0L) return "Invocation audit has no stable managed consumer identity";
    if (record.sourceRevisionId() == null || record.sourceRevisionId() <= 0L) return "Invocation audit has no pinned source revision";
    if (record.createTime() == null) return "Invocation audit has no observation time";
    return null;
  }

  private String evidenceRef(InvocationRecord record) {
    return record == null || record.id() == null ? null : "invocation:" + record.id();
  }
}
