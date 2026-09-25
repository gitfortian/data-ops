package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Idempotently persists normalized successful usage; source audit remains owned by source domains. */
@Service
@RequiredArgsConstructor
public class UsageEvidenceService {

  private final UsageEvidenceRepository repository;

  public UsageEvidence normalize(UsageEvidenceCommand command) {
    Objects.requireNonNull(command, "command");
    return repository.findByDeduplicationId(command.projectId(), command.deduplicationId())
        .orElseGet(() -> repository.save(new UsageEvidence(
            null,
            command.projectId(),
            command.productKey(),
            command.sourceVersion(),
            command.consumerRef(),
            command.observedAt(),
            command.consumptionMode(),
            UsageOutcome.SUCCESS,
            command.provider(),
            command.providerEvidenceRef(),
            command.deduplicationId(),
            LocalDateTime.now())));
  }

  public List<UsageEvidence> list(
      Long projectId, ProductKey productKey, ConsumerRef consumerRef, int limit) {
    Objects.requireNonNull(projectId, "projectId");
    return repository.list(projectId, productKey, consumerRef, Math.max(1, Math.min(200, limit)));
  }

  public record UsageEvidenceCommand(
      Long projectId,
      ProductKey productKey,
      SourceVersionRef sourceVersion,
      ConsumerRef consumerRef,
      LocalDateTime observedAt,
      ConsumptionMode consumptionMode,
      String provider,
      String providerEvidenceRef,
      String deduplicationId) {

    public UsageEvidenceCommand {
      Objects.requireNonNull(projectId, "projectId");
      Objects.requireNonNull(productKey, "productKey");
      Objects.requireNonNull(sourceVersion, "sourceVersion");
      Objects.requireNonNull(consumerRef, "consumerRef");
      Objects.requireNonNull(observedAt, "observedAt");
      Objects.requireNonNull(consumptionMode, "consumptionMode");
      provider = requireText(provider, "provider");
      providerEvidenceRef = requireText(providerEvidenceRef, "providerEvidenceRef");
      deduplicationId = requireText(deduplicationId, "deduplicationId");
    }

    private static String requireText(String value, String field) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(field + " must not be blank");
      }
      return value.trim();
    }
  }
}
