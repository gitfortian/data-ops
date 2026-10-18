package io.yak.ops.business.modeling.derive;

import io.yak.ops.business.modeling.derive.AdsApplicationDesignService.Contract;
import io.yak.ops.business.modeling.derive.AdsApplicationDesignService.MetricPublicationPin;
import io.yak.ops.business.modeling.derive.AdsApplicationDesignService.Saved;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Evidence;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Reference;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Status;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Read-only ADS handoff evidence projection.
 *
 * <p>Only Metric has a stable cross-domain reference verifier today. Other domains are explicitly
 * marked NOT_CONNECTED until an owner-approved stable association and read API exist. This service
 * never queries execution lists, runs jobs, executes SQL, or grants access.</p>
 */
@Component
public class AdsApplicationEvidenceService {

  public record DomainEvidence(
      String domainCode,
      String status,
      String sourceStatus,
      String sourceDomain,
      String summary,
      String evidenceId,
      String version,
      String digest) {}

  public record Snapshot(
      long modelId,
      Long contractRevision,
      String contractSha256,
      String checkedAt,
      List<DomainEvidence> domains) {}

  private final AdsApplicationDesignService contracts;
  private final MetricPublicationEvidenceProvider metricEvidence;

  public AdsApplicationEvidenceService(
      AdsApplicationDesignService contracts,
      MetricPublicationEvidenceProvider metricEvidence) {
    this.contracts = contracts;
    this.metricEvidence = metricEvidence;
  }

  public Snapshot inspect(long modelId) {
    Optional<Saved> saved = contracts.inspect(modelId);
    List<DomainEvidence> domains = new ArrayList<>();
    if (saved.isEmpty()) {
      domains.add(item("METRIC", "NOT_CONFIGURED", null, "Metric",
          "当前尚无已保存的 ADS 合同，因此没有可核验的 Metric 引用。",
          null, null, null));
    } else {
      Contract contract = saved.get().contract();
      MetricPublicationPin pin = contract == null ? null : contract.metricPublicationPin();
      if (pin == null) {
        if (contract != null && contract.metricVersionId() != null) {
          domains.add(item("METRIC", "UNKNOWN", "LEGACY_INCOMPLETE", "Metric",
              "旧合同仅保存 Metric 版本 ID，缺少完整发布身份；不会猜测补齐。",
              null, null, null));
        } else {
          domains.add(item("METRIC", "NOT_CONFIGURED", null, "Metric",
              "当前 ADS 合同未配置 Metric 引用。",
              null, null, null));
        }
      } else {
        domains.add(inspectMetric(pin));
      }
    }

    domains.add(item("DATADEV_RUNTIME", "NOT_CONNECTED", null, "DataDev / Runtime",
        "尚无 ADS 合同修订到 TaskRevision / execution 的稳定、获批关联。不会按名称、nodeId 或最近运行推断。",
        null, null, null));
    domains.add(item("QUALITY", "NOT_CONNECTED", null, "Quality",
        "尚无从 ADS 合同到质量规则及执行结果的稳定关联；无质量证据不代表质量通过。",
        null, null, null));
    domains.add(item("DATASET", "NOT_CONNECTED", null, "Dataset",
        "ADS 合同尚无可核验的 Dataset 身份引用；不会按表名或名称相似度自动关联。",
        null, null, null));
    domains.add(item("DATA_SERVICE", "NOT_CONNECTED", null, "Data Service",
        "ADS 合同尚无可核验的 Data Service 引用；服务存在或运行正常不等于该应用可访问。",
        null, null, null));
    domains.add(item("CONSUMPTION_AUTHORIZATION", "NOT_CONNECTED", null, "Consumption / Access Policy",
        "尚无消费方 principal 与授权决策的稳定引用；订阅、服务启用或 Key 存在均不视为授权通过。",
        null, null, null));

    Saved current = saved.orElse(null);
    return new Snapshot(
        modelId,
        current == null ? null : current.revision(),
        current == null ? null : current.contractSha256(),
        Instant.now().toString(),
        List.copyOf(domains));
  }

  private DomainEvidence inspectMetric(MetricPublicationPin pin) {
    Status sourceStatus;
    try {
      Evidence evidence = metricEvidence.inspect(new Reference(
          pin.metricId(), pin.metricVersionId(), pin.metricVersion(),
          pin.snapshotDigest(), pin.publicationEventId()));
      sourceStatus = evidence == null || evidence.status() == null
          ? Status.UNAVAILABLE : evidence.status();
    } catch (RuntimeException unavailable) {
      sourceStatus = Status.UNAVAILABLE;
    }

    String status = switch (sourceStatus) {
      case VERIFIED_ACTIVE -> "VERIFIED";
      case FORBIDDEN -> "FORBIDDEN";
      case WITHDRAWN, NOT_CURRENTLY_PUBLISHED, REFERENCE_MISMATCH,
          VERSION_MISMATCH, DIGEST_MISMATCH -> "STALE";
      case NOT_FOUND, UNAVAILABLE -> "UNKNOWN";
    };
    boolean safeToExposePin = sourceStatus != Status.FORBIDDEN
        && sourceStatus != Status.NOT_FOUND && sourceStatus != Status.UNAVAILABLE;
    return item(
        "METRIC",
        status,
        sourceStatus.name(),
        "Metric",
        metricSummary(sourceStatus),
        safeToExposePin ? String.valueOf(pin.publicationEventId()) : null,
        safeToExposePin ? "V" + pin.metricVersion() : null,
        safeToExposePin ? pin.snapshotDigest() : null);
  }

  private static String metricSummary(Status status) {
    return switch (status) {
      case VERIFIED_ACTIVE -> "完整发布身份、版本、摘要与当前有效发布匹配。";
      case WITHDRAWN -> "该发布已撤回；历史合同保持原始引用，不自动改绑。";
      case NOT_CURRENTLY_PUBLISHED -> "该发布已不是当前有效发布；历史合同保持原始引用。";
      case NOT_FOUND -> "当前项目内无法解析该发布引用；不区分对象缺失与跨项目不可见。";
      case REFERENCE_MISMATCH -> "发布事件与冻结的 Metric 身份不匹配。";
      case VERSION_MISMATCH -> "Metric 不可变版本身份不匹配。";
      case DIGEST_MISMATCH -> "Metric 快照摘要不匹配。";
      case FORBIDDEN -> "当前用户无权读取 Metric 发布证据。";
      case UNAVAILABLE -> "Metric 原域暂不可用或响应异常；未将失败当作通过。";
    };
  }

  private static DomainEvidence item(
      String domainCode,
      String status,
      String sourceStatus,
      String sourceDomain,
      String summary,
      String evidenceId,
      String version,
      String digest) {
    return new DomainEvidence(domainCode, status, sourceStatus, sourceDomain, summary,
        evidenceId, version, digest);
  }
}
