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
import io.yak.ops.business.dataset.DatasetSuccessfulQueryEvent;
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
    return normalize(
        projectId,
        trace.queryId(),
        trace.datasetId(),
        trace.datasetVersionId(),
        trace.datasetVersionNo(),
        trace.subjectType(),
        trace.subjectSourceDomain(),
        trace.subjectSourceIdentity(),
        trace.subjectDisplayHint(),
        trace.startedAt());
  }

  public UsageNormalizationResult normalize(Long projectId, DatasetSuccessfulQueryEvent event) {
    if (event == null) return UsageNormalizationResult.gap(null, "Dataset query evidence is missing");
    return normalize(
        projectId == null ? event.projectId() : projectId,
        event.queryId(),
        event.datasetId(),
        event.datasetVersionId(),
        event.datasetVersionNo(),
        event.subjectType(),
        event.subjectSourceDomain(),
        event.subjectSourceIdentity(),
        event.subjectDisplayHint(),
        event.startedAt());
  }

  private UsageNormalizationResult normalize(
      Long projectId,
      String queryId,
      long datasetId,
      Long datasetVersionId,
      Integer datasetVersionNo,
      String subjectType,
      String subjectSourceDomain,
      String subjectSourceIdentity,
      String subjectDisplayHint,
      java.time.Instant startedAt) {
    String ref = queryId == null ? null : "query:" + queryId;
    String gap = attributionGap(
        projectId, queryId, datasetId, datasetVersionId, subjectType,
        subjectSourceDomain, subjectSourceIdentity, startedAt);
    if (gap != null) {
      return UsageNormalizationResult.gap(ref, gap);
    }
    try {
      UsageEvidence evidence = usageEvidenceService.normalize(
          new UsageEvidenceService.UsageEvidenceCommand(
              projectId,
              new ProductKey(ProductType.DATASET, Long.toString(datasetId)),
              new SourceVersionRef(
                  datasetVersionId.toString(),
                  datasetVersionNo == null ? null : "v" + datasetVersionNo),
              new ConsumerRef(
                  ConsumerType.USER,
                  subjectSourceDomain,
                  subjectSourceIdentity,
                  subjectDisplayHint),
              LocalDateTime.ofInstant(startedAt, ZoneOffset.UTC),
              ConsumptionMode.QUERY,
              PROVIDER,
              ref,
              PROVIDER + ":" + queryId));
      return UsageNormalizationResult.normalized(evidence);
    } catch (RuntimeException failure) {
      return UsageNormalizationResult.unavailable(
          ref,
          failure.getMessage() == null ? "Usage normalization failed" : failure.getMessage());
    }
  }

  private String attributionGap(
      Long projectId,
      String queryId,
      long datasetId,
      Long datasetVersionId,
      String subjectType,
      String subjectSourceDomain,
      String subjectSourceIdentity,
      java.time.Instant startedAt) {
    if (projectId == null || projectId <= 0L) return "Dataset query evidence has no Project Space";
    if (queryId == null || queryId.isBlank()) return "Dataset query has no stable evidence id";
    if (datasetId <= 0L) return "Dataset query has no Dataset identity";
    if (datasetVersionId == null || datasetVersionId <= 0L) {
      return "Dataset query has no exact immutable DatasetVersion";
    }
    if (!"USER".equals(subjectType)) return "Dataset query has no supported USER subject";
    if (subjectSourceDomain == null || subjectSourceDomain.isBlank()) {
      return "Dataset query has no subject source domain";
    }
    if (subjectSourceIdentity == null || subjectSourceIdentity.isBlank()) {
      return "Dataset query has no stable execution-time subject identity";
    }
    if (startedAt == null) return "Dataset query has no observation time";
    return null;
  }

  private String evidenceRef(DatasetQueryPerformance trace) {
    return trace == null || trace.queryId() == null ? null : "query:" + trace.queryId();
  }
}
