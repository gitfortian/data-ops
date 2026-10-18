package io.yak.ops.business.metric.publication;

import io.yak.ops.business.metric.dao.model.MetricActivePublicationPO;
import io.yak.ops.business.metric.dao.model.MetricPublicationEventPO;
import io.yak.ops.business.metric.dao.model.MetricVersionPO;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.business.metric.support.MetricSnapshotDigest;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Evidence;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Reference;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Status;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metric-owned, project-scoped read boundary for ADS Metric publication pins.
 *
 * <p>It never publishes, withdraws, or edits a Metric. New ADS contract validation locks the
 * owning Metric row so a concurrent publish/withdraw cannot change the active pointer between
 * verification and persistence in the caller's transaction.</p>
 */
@Service
public class MetricPublicationEvidenceProviderImpl implements MetricPublicationEvidenceProvider {

  private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
  private static final String PUBLISHED = "PUBLISHED";
  private static final String WITHDRAWN = "WITHDRAWN";

  private final CurrentProject currentProject;
  private final MetricPublicationReadAccess readAccess;
  private final MetricRepository metrics;
  private final MetricVersionRepository versions;
  private final MetricPublicationRepository publications;

  public MetricPublicationEvidenceProviderImpl(
      CurrentProject currentProject,
      MetricPublicationReadAccess readAccess,
      MetricRepository metrics,
      MetricVersionRepository versions,
      MetricPublicationRepository publications) {
    this.currentProject = currentProject;
    this.readAccess = readAccess;
    this.metrics = metrics;
    this.versions = versions;
    this.publications = publications;
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Evidence inspect(Reference reference) {
    return resolveSafely(reference, false);
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Evidence verifyForNewContract(Reference reference) {
    return resolveSafely(reference, true);
  }

  private Evidence resolveSafely(Reference reference, boolean lockMetric) {
    final Long projectId;
    try {
      projectId = currentProject.requireProjectId();
    } catch (RuntimeException missingProject) {
      return new Evidence(Status.UNAVAILABLE);
    }
    if (!readAccess.canRead(projectId)) {
      return new Evidence(Status.FORBIDDEN);
    }
    if (!valid(reference)) {
      return new Evidence(Status.REFERENCE_MISMATCH);
    }
    try {
      return resolve(reference, lockMetric);
    } catch (RuntimeException unavailable) {
      // Evidence failures are never converted to a positive result or to "not found".
      return new Evidence(Status.UNAVAILABLE);
    }
  }

  private Evidence resolve(Reference reference, boolean lockMetric) {
    if (lockMetric) {
      if (publications.lockCurrentMetricVersion(reference.metricId()) == null) {
        return new Evidence(Status.NOT_FOUND);
      }
    }

    Metric metric = metrics.findById(reference.metricId()).orElse(null);
    if (metric == null) {
      // Project-scoped lookups deliberately make a cross-project ID indistinguishable from absent.
      return new Evidence(Status.NOT_FOUND);
    }
    MetricPublicationEventPO event = publications.findEvent(reference.publicationEventId());
    if (event == null) {
      return new Evidence(Status.NOT_FOUND);
    }
    if (!PUBLISHED.equals(event.getEventType())
        || !Objects.equals(event.getMetricId(), reference.metricId())) {
      return new Evidence(Status.REFERENCE_MISMATCH);
    }
    if (!Objects.equals(event.getMetricVersionId(), reference.metricVersionId())
        || !Objects.equals(event.getMetricVersion(), reference.metricVersion())) {
      return new Evidence(Status.VERSION_MISMATCH);
    }

    MetricVersionPO version = versions.findByMetricAndVersion(
        reference.metricId(), reference.metricVersion());
    if (version == null
        || !Objects.equals(version.getMetricId(), reference.metricId())
        || !Objects.equals(version.getId(), reference.metricVersionId())
        || !Objects.equals(version.getVersion(), reference.metricVersion())) {
      return new Evidence(Status.VERSION_MISMATCH);
    }

    String actualDigest = MetricSnapshotDigest.sha256(version.getSnapshot());
    if (!Objects.equals(actualDigest, reference.snapshotDigest())
        || !Objects.equals(event.getSnapshotDigest(), actualDigest)) {
      return new Evidence(Status.DIGEST_MISMATCH);
    }

    MetricActivePublicationPO active = publications.findActive(reference.metricId());
    if (metric.status() == MetricStatus.ENABLED && matches(active, reference)) {
      return new Evidence(Status.VERIFIED_ACTIVE);
    }

    boolean explicitlyWithdrawn = publications.listEvents(reference.metricId()).stream()
        .anyMatch(candidate -> WITHDRAWN.equals(candidate.getEventType())
            && Objects.equals(candidate.getSubjectPublicationId(), reference.publicationEventId()));
    if (explicitlyWithdrawn) {
      return new Evidence(Status.WITHDRAWN);
    }
    return new Evidence(Status.NOT_CURRENTLY_PUBLISHED);
  }

  private static boolean matches(MetricActivePublicationPO active, Reference reference) {
    return active != null
        && Objects.equals(active.getPublicationEventId(), reference.publicationEventId())
        && Objects.equals(active.getMetricId(), reference.metricId())
        && Objects.equals(active.getMetricVersionId(), reference.metricVersionId())
        && Objects.equals(active.getMetricVersion(), reference.metricVersion())
        && Objects.equals(active.getSnapshotDigest(), reference.snapshotDigest());
  }

  private static boolean valid(Reference reference) {
    return reference != null
        && reference.metricId() != null && reference.metricId() > 0
        && reference.metricVersionId() != null && reference.metricVersionId() > 0
        && reference.metricVersion() != null && reference.metricVersion() > 0
        && reference.publicationEventId() != null && reference.publicationEventId() > 0
        && reference.snapshotDigest() != null
        && SHA256.matcher(reference.snapshotDigest()).matches();
  }
}
