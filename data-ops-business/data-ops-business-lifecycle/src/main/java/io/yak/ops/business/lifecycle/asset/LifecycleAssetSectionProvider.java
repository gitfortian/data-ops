package io.yak.ops.business.lifecycle.asset;

import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.business.asset.api.AssetStatusTtlFacts;
import io.yak.ops.business.lifecycle.config.ConditionalOnLifecyclePersistence;
import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionEvidence;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionSummary;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Lifecycle-owned read adapter for the model TTL facts shown in Asset Detail. */
@Component
@ConditionalOnLifecyclePersistence
@RequiredArgsConstructor
public class LifecycleAssetSectionProvider implements SectionProvider {

  public record LifecycleSummary(
      Boolean policyApplied, String policyCode, String bindingSource, String state)
      implements SectionSummary {
    @Override public Map<String, Object> values() {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("policyApplied", policyApplied);
      values.put("policyCode", policyCode);
      values.put("bindingSource", bindingSource);
      values.put("state", state);
      return values;
    }
  }

  @Override
  public java.util.Set<String> supportedSourceTypes() {
    return java.util.Set.of("MODEL");
  }

  private final AssetStatusTtlFacts ttlFacts;

  @Override
  public SectionType sectionType() {
    return SectionType.LIFECYCLE;
  }

  @Override
  public boolean supports(SectionContext context) {
    return "MODEL".equals(context.sourceType()) && context.sourceId() != null;
  }

  @Override
  public SectionContract query(SectionContext context) {
    Instant observedAt = Instant.now();
    AssetStatusTtlFacts.TtlFacts facts = ttlFacts.ttlFacts(context.sourceId()).orElse(null);
    if (facts == null) {
      return response(context, SectionStatus.UNAVAILABLE, new LifecycleSummary(null, null, null, null),
          "Lifecycle 域无法解析该模型的 TTL 事实", observedAt);
    }
    if (!facts.policyApplied()) {
      return response(context, SectionStatus.EMPTY,
          new LifecycleSummary(false, null, value(facts.bindingSource()), null),
          "当前模型尚未命中有效生命周期策略", observedAt);
    }
    return response(context, SectionStatus.OK,
        new LifecycleSummary(true, value(facts.policyCode()), value(facts.bindingSource()),
            value(facts.state())), null, observedAt);
  }

  private static SectionContract response(
      SectionContext context, SectionStatus status, LifecycleSummary summary,
      String reason, Instant observedAt) {
    String sourceId = context.sourceId();
    String assetId = context.attributes().get("returnAssetId");
    List<SectionAction> actions = assetId == null ? List.of() : List.of(
        new SectionAction("查看生命周期监控", "/data-lifecycle/monitor?modelId=" + sourceId
            + "&returnAssetId=" + assetId, sourceId));
    return new AssetSectionResult(
        SectionType.LIFECYCLE, status, "LIFECYCLE", summary, reason,
        null, actions,
        status == SectionStatus.EMPTY ? List.of()
            : List.of(new SectionEvidence("LIFECYCLE", sourceId, observedAt)),
        new SectionProvenance("LIFECYCLE", sourceId, observedAt),
        new SectionCapability(true, status != SectionStatus.UNAVAILABLE, reason));
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
