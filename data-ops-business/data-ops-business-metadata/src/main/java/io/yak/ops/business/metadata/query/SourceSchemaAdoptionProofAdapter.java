package io.yak.ops.business.metadata.query;

import io.yak.ops.spi.semantic.SourceSchemaAdoptionProof;
import java.util.HashSet;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Delegate all source authorization and complete evidence to its Metadata owner. */
@Component
public final class SourceSchemaAdoptionProofAdapter implements SourceSchemaAdoptionProof {
  private final AuthorizedPhysicalScopeEvidenceService authorized;
  public SourceSchemaAdoptionProofAdapter(AuthorizedPhysicalScopeEvidenceService authorized) {
    this.authorized = authorized;
  }
  @Override public void assertCurrent(Expected expected) {
    if (expected == null || expected.projectId() <= 0 || expected.dataSourceId() <= 0
        || expected.tableAssetKeys().isEmpty() || expected.tableAssetKeys().size() > 20
        || expected.tableAssetKeys().stream().anyMatch(v -> v == null || v.isBlank())
        || new HashSet<>(expected.tableAssetKeys()).size() != expected.tableAssetKeys().size())
      throw new IllegalArgumentException("[F039_ADOPTION_SOURCE_INVALID]");
    var actual = authorized.read(expected.dataSourceId(),expected.tableAssetKeys());
    if (actual.projectId() != expected.projectId()
        || !Objects.equals(actual.collectJobId(),expected.captureId())
        || !Objects.equals(actual.fingerprint(),expected.evidenceFingerprint())
        || actual.tables().size()!=expected.tableAssetKeys().size())
      throw new IllegalStateException("[F039_ADOPTION_SOURCE_DRIFT]");
  }
}
