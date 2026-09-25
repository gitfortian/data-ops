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
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Converts source-owned Dataset query diagnostics into normalized successful usage evidence. */
@Component
@RequiredArgsConstructor
public class DatasetUsageEvidenceNormalizer {

  static final String PROVIDER = "DATASET_QUERY_PERFORMANCE";
  private final UsageEvidenceService usageEvidenceService;

  public UsageNormalizationResult normalize(Long projectId, DatasetQueryPerformance trace) {
    String ref = evidenceRef(trace);
    if (trace == null) {
      return UsageNormalizationResult.gap(null, "Dataset query evidence is missing");
    }
    if (trace.status() != DatasetQueryStatus.SUCCESS) {
      return UsageNormalizationResult.ignored(ref, "Failed or rejected query is diagnostic evidence, not usage");
    }
    String gap = attributionGap(projectId, trace);
    if (gap != null) {
      return UsageNormalizationResult.gap(ref, gap);
    }
    try {
      UsageEvidence evidence = usageEvidenceService.normalize(
          new UsageEvidenceService.UsageEvidenceCommand(
              projectId,
              new ProductKey(ProductType.DATASET, Long.toString(trace.datasetId())),
              new SourceVersionRef(
                  trace.datasetVersionId().toString(),
                  trace.datasetVersionNo() == null ? null : "v" + trace.datasetVersionNo()),
              new ConsumerRef(
                  ConsumerType.USER,
                  trace.subjectSourceDomain(),
                  trace.subjectSourceIdentity(),
                  trace.subjectDisplayHint()),
              LocalDateTime.ofInstant(trace.startedAt(), ZoneOffset.UTC),
              ConsumptionMode.QUERY,
              PROVIDER,
              ref,
              PROVIDER + ":" + trace.queryId()));
      return UsageNormalizationResult.normalized(evidence);
    } catch (RuntimeException failure) {
      return UsageNormalizationResult.unavailable(
          ref,
          failure.getMessage() == null ? "Usage normalization failed" : failure.getMessage());
    }
  }

  private String attributionGap(Long projectId, DatasetQueryPerformance trace) {
    if (projectId == null || projectId <= 0L) return "Dataset query evidence has no Project Space";
    if (trace.queryId() == null || trace.queryId().isBlank()) return "Dataset query has no stable evidence id";
    if (trace.datasetId() <= 0L) return "Dataset query has no Dataset identity";
    if (trace.datasetVersionId() == null || trace.datasetVersionId() <= 0L) {
      return "Dataset query has no exact immutable DatasetVersion";
    }
    if (!"USER".equals(trace.subjectType())) return "Dataset query has no supported USER subject";
    if (trace.subjectSourceDomain() == null || trace.subjectSourceDomain().isBlank()) {
      return "Dataset query has no subject source domain";
    }
    if (trace.subjectSourceIdentity() == null || trace.subjectSourceIdentity().isBlank()) {
      return "Dataset query has no stable execution-time subject identity";
    }
    if (trace.startedAt() == null) return "Dataset query has no observation time";
    return null;
  }

  private String evidenceRef(DatasetQueryPerformance trace) {
    return trace == null || trace.queryId() == null ? null : "query:" + trace.queryId();
  }
}
