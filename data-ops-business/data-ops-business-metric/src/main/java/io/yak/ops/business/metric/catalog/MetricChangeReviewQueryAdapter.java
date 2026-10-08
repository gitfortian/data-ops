package io.yak.ops.business.metric.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metric.api.MetricChangeReviewQueryApi;
import io.yak.ops.business.metric.api.MetricExplanationQueryApi;
import io.yak.ops.business.metric.domain.MetricValidationEvidence;
import io.yak.ops.business.metric.impact.MetricObservedUsageProvider;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.business.metric.repository.MetricUsageRepository;
import io.yak.ops.business.metric.repository.MetricValidationEvidenceRepository;
import io.yak.ops.business.metric.support.MetricSnapshotDigest;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Independent evidence reads are not an atomic snapshot or a publication decision. */
@Component
@RequiredArgsConstructor
public class MetricChangeReviewQueryAdapter implements MetricChangeReviewQueryApi {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ActionAuthorization authorization;
  private final MetricExplanationQueryApi explanations;
  private final MetricPublicationRepository publications;
  private final MetricValidationEvidenceRepository validations;
  private final MetricUsageRepository usages;
  private final ObjectProvider<MetricObservedUsageProvider> observedProviders;

  @Override public Context prepare(long metricId, int version) {
    authorization.requirePermission(MetricPermissionCode.READ);
    var current = explanations.require(metricId, version);
    var active = publications.findActive(metricId);
    if (active == null) return new Context("NO_BASELINE", metricId, version, null, null, null,
        null, LocalDateTime.now().toString(), List.of(), List.of(), List.of());
    var baseline = explanations.requireSnapshot(metricId, active.getMetricVersion());
    if (active.getPublicationEventId() == null || !Objects.equals(active.getMetricVersionId(), baseline.versionId())
        || !Objects.equals(active.getSnapshotDigest(), baseline.definition())) throw new IllegalStateException("生效发布版本无法核对");
    var event = publications.findEvent(active.getPublicationEventId());
    if (event == null || !"PUBLISHED".equals(event.getEventType()) || !Objects.equals(event.getMetricId(), metricId)
        || !Objects.equals(event.getMetricVersion(), active.getMetricVersion())
        || !Objects.equals(event.getMetricVersionId(), active.getMetricVersionId())
        || !Objects.equals(event.getSnapshotDigest(), active.getSnapshotDigest())) throw new IllegalStateException("发布账本无法核对");
    var before = new LinkedHashMap<String, MetricExplanationQueryApi.Fact>();
    var after = new LinkedHashMap<String, MetricExplanationQueryApi.Fact>();
    baseline.facts().forEach(f -> before.put(f.key(), f)); current.facts().forEach(f -> after.put(f.key(), f));
    var keys = new java.util.TreeSet<String>(before.keySet()); keys.addAll(after.keySet());
    var differences = new ArrayList<Difference>(); var facts = new ArrayList<Fact>();
    for (String key : keys) {
      var left = before.get(key); var right = after.get(key);
      String l = left == null ? null : left.value(); String r = right == null ? null : right.value();
      if (!Objects.equals(l, r)) {
        String label = right == null ? left.label() : right.label();
        differences.add(new Difference(key, label, l, r));
        facts.add(new Fact("before." + key, "发布 v" + baseline.version() + " · " + label, l == null ? "（未记录）" : l));
        facts.add(new Fact("after." + key, "草稿 v" + version + " · " + label, r == null ? "（未记录）" : r));
      } else if (right != null) {
        facts.add(new Fact("after." + key, "草稿 v" + version + " · " + right.label(), r));
      }
    }
    var coverage = new ArrayList<Coverage>();
    String validationIdentity = validation(metricId, current, facts, coverage); references(metricId, facts, coverage);
    coverage.add(new Coverage("readiness", "当前完整发布门禁", "UNAVAILABLE", "本场景尚无完整门禁的安全有界读取，请在原治理面板核对；本说明不决定发布资格。"));
    coverage.add(new Coverage("dependencies", "当前依赖健康", "UNAVAILABLE", "本场景尚无安全有界投影，请在原影响页面核对。"));
    coverage.add(new Coverage("lineage", "技术血缘", "UNAVAILABLE", "本场景未读取血缘，不代表无下游关系。请在原影响页面核对其声明的覆盖范围。"));
    boolean hasObserved = observedProviders.stream().findFirst().isPresent();
    coverage.add(new Coverage("observed", "实际消费证据", hasObserved ? "UNAVAILABLE" : "NOT_APPLICABLE", hasObserved
        ? "已注册 provider 尚未接入本场景安全有界读取，无法判断实际消费。" : "尚无稳定指标到消费证据的 provider，不代表没有真实消费。"));
    coverage.forEach(c -> facts.add(new Fact("coverage." + c.key(), c.label(), c.status() + "：" + c.description())));
    var finalCurrent = explanations.require(metricId, version); var finalActive = publications.findActive(metricId);
    if (!current.definition().equals(finalCurrent.definition()) || current.versionId() != finalCurrent.versionId()
        || finalActive == null || !Objects.equals(active.getPublicationEventId(), finalActive.getPublicationEventId())
        || !Objects.equals(active.getSnapshotDigest(), finalActive.getSnapshotDigest())
        || !Objects.equals(active.getMetricVersionId(), finalActive.getMetricVersionId())
        || !Objects.equals(active.getMetricVersion(), finalActive.getMetricVersion())) throw new IllegalStateException("版本或发布指针已变化，请重新准备");
    // Read time is deliberately outside the digest; only evidence identities/results participate.
    String definition = MetricSnapshotDigest.sha256(encode(List.of(current.versionId(), current.definition(), baseline.versionId(),
        baseline.definition(), active.getPublicationEventId(), validationIdentity, differences, facts, coverage)));
    var result = new Context(differences.isEmpty() ? "UNCHANGED" : "READY", metricId, version, baseline.versionId(), baseline.version(),
        active.getPublicationEventId(), definition, LocalDateTime.now().toString(), List.copyOf(differences), List.copyOf(facts), List.copyOf(coverage));
    if (encode(result).length() > 24000) throw new IllegalStateException("变更事实超过解释范围，请在原差异页面核对");
    return result;
  }

  private String validation(long id, MetricExplanationQueryApi.Context current, List<Fact> facts, List<Coverage> coverage) {
    try {
      var value = validations.findLatest(id, current.version());
      if (value == null) { coverage.add(new Coverage("validation", "当前草稿最新验证", "EMPTY", "此精确草稿版本尚无验证证据。")); return "EMPTY"; }
      if (!Objects.equals(value.metricId(), id) || value.metricVersion() != current.version()
          || !Objects.equals(value.metricVersionId(), current.versionId()) || !Objects.equals(value.snapshotDigest(), current.definition())
          || value.evidenceId() == null) throw new IllegalStateException("验证对象不一致");
      if (value.providerState() != MetricValidationEvidence.ProviderState.READY) {
        coverage.add(new Coverage("validation", "当前草稿最新验证", value.providerState().name(), "验证 provider 未提供可核对结果，请回原治理面板。"));
        return value.evidenceId() + ":" + value.providerState() + ":" + value.result() + ":" + value.checkedAt();
      }
      var projected = new ArrayList<Fact>();
      projected.add(new Fact("validation.result", "最新精确验证结果", value.result().name()));
      projected.add(new Fact("validation.identity", "验证依据", "evidence:" + value.evidenceId() + " / v" + value.metricVersion()
          + " / " + bounded(value.provider()) + " / " + value.checkedAt()));
      if (value.issues().size() > 20) throw new IllegalStateException("验证问题超过范围");
      for (int i = 0; i < value.issues().size(); i++) {
        var issue = value.issues().get(i);
        projected.add(new Fact("validation.issue." + i, "验证问题 " + (i + 1), bounded(issue.code()) + " / "
            + bounded(issue.field()) + " / " + issue.severity() + " / " + bounded(issue.message())));
      }
      facts.addAll(projected);
      coverage.add(new Coverage("validation", "当前草稿最新验证", "READY", "已有证据仅绑定本次精确版本；不表示现在可以发布。"));
      return value.evidenceId().toString();
    } catch (ActionAccessDeniedException | SecurityException denied) {
      coverage.add(new Coverage("validation", "当前草稿最新验证", "FORBIDDEN", "无权读取验证证据，请回原页面核对。"));
      return "FORBIDDEN";
    } catch (RuntimeException failed) {
      coverage.add(new Coverage("validation", "当前草稿最新验证", "UNAVAILABLE", "验证证据暂无法核对，请回原治理面板。"));
      return "UNAVAILABLE";
    }
  }
  private void references(long id, List<Fact> facts, List<Coverage> coverage) {
    try {
      var rows = usages.listByMetricBounded(id, 21); var projected = new ArrayList<Fact>();
      for (int i = 0; i < Math.min(20, rows.size()); i++) {
        var row = rows.get(i);
        if (!Objects.equals(row.metricId(), id) || row.id() == null) throw new IllegalStateException("引用对象不一致");
        projected.add(new Fact("reference." + row.id(), "已登记声明引用", "record:" + row.id() + " / " + bounded(row.usageType())
            + ":" + row.usageId() + " / version:" + (row.metricVersion() == null ? "未知" : row.metricVersion()) + " / " + row.createdAt()));
      }
      facts.addAll(projected);
      coverage.add(new Coverage("references", "声明引用", rows.isEmpty() ? "EMPTY" : "READY", rows.size() > 20
          ? "仅前20条声明记录，仍有未展示记录；不是完整影响或调用量。" : "当前读取至多20条声明记录；不证明实际消费，也不授予下游访问权限。"));
    } catch (ActionAccessDeniedException | SecurityException denied) {
      coverage.add(new Coverage("references", "声明引用", "FORBIDDEN", "无权读取声明引用，请回原影响页面。"));
    } catch (RuntimeException failed) {
      coverage.add(new Coverage("references", "声明引用", "UNAVAILABLE", "声明引用暂不可读，不能推断无影响。"));
    }
  }
  private static String bounded(String value) {
    if (value == null) return "未记录";
    if (value.length() > 512) throw new IllegalStateException("治理事实超限");
    return value;
  }
  private static String encode(Object value) {
    try { return JSON.writeValueAsString(value); }
    catch (JsonProcessingException failed) { throw new IllegalStateException("变更事实无法核对", failed); }
  }
}
