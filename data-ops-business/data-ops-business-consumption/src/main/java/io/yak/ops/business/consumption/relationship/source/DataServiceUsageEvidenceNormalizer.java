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
import io.yak.ops.business.dataservice.domain.DataServiceSuccessfulInvocationEvent;
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
    return normalize(
        record.id(), record.projectId(), record.apiId(), record.consumerId(),
        record.apiKeyName(), record.sourceRevisionId(), record.sourceRevisionNo(), record.createTime());
  }

  public UsageNormalizationResult normalize(DataServiceSuccessfulInvocationEvent event) {
    if (event == null) {
      return UsageNormalizationResult.gap(null, "Data Service invocation evidence is missing");
    }
    return normalize(
        event.id(), event.projectId(), event.apiId(), event.consumerId(), event.consumerName(),
        event.sourceRevisionId(), event.sourceRevisionNo(), event.observedAt());
  }

  private UsageNormalizationResult normalize(
      Long id,
      Long projectId,
      Long apiId,
      Long consumerId,
      String consumerName,
      Long sourceRevisionId,
      Integer sourceRevisionNo,
      java.time.LocalDateTime observedAt) {
    String ref = id == null ? null : "invocation:" + id;
    String gap = attributionGap(id, projectId, apiId, consumerId, sourceRevisionId, observedAt);
    if (gap != null) {
      return UsageNormalizationResult.gap(ref, gap);
    }
    try {
      UsageEvidence evidence = usageEvidenceService.normalize(
          new UsageEvidenceService.UsageEvidenceCommand(
              projectId,
              new ProductKey(ProductType.DATA_SERVICE, apiId.toString()),
              new SourceVersionRef(
                  sourceRevisionId.toString(),
                  sourceRevisionNo == null ? null : "r" + sourceRevisionNo),
              new ConsumerRef(
                  ConsumerType.DATA_SERVICE,
                  "DATA_SERVICE_CONSUMER",
                  consumerId.toString(),
                  consumerName),
              observedAt,
              ConsumptionMode.API_INVOKE,
              PROVIDER,
              ref,
              PROVIDER + ":" + id));
      return UsageNormalizationResult.normalized(evidence);
    } catch (RuntimeException failure) {
      return UsageNormalizationResult.unavailable(
          ref,
          failure.getMessage() == null ? "Usage normalization failed" : failure.getMessage());
    }
  }

  private String attributionGap(
      Long id, Long projectId, Long apiId, Long consumerId, Long sourceRevisionId,
      java.time.LocalDateTime observedAt) {
    if (id == null) return "Invocation audit has no stable evidence id";
    if (projectId == null || projectId <= 0L) return "Invocation audit has no Project Space";
    if (apiId == null || apiId <= 0L) return "Invocation audit has no Data Service identity";
    if (consumerId == null || consumerId <= 0L) return "Invocation audit has no stable managed consumer identity";
    if (sourceRevisionId == null || sourceRevisionId <= 0L) return "Invocation audit has no pinned source revision";
    if (observedAt == null) return "Invocation audit has no observation time";
    return null;
  }

  private String evidenceRef(InvocationRecord record) {
    return record == null || record.id() == null ? null : "invocation:" + record.id();
  }
}
