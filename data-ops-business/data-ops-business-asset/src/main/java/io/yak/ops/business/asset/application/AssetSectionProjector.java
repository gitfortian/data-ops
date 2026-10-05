package io.yak.ops.business.asset.application;

import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.business.asset.application.AssetAppService.AssetView;
import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionSummary;
import io.yak.ops.spi.section.SectionType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single projection shared by HTTP and governance consumers. */
public final class AssetSectionProjector {
  private AssetSectionProjector() {}
  public static AssetSectionResult project(
      SectionType type, AssetDiscoverService.SectionView view, AssetView asset) {
    if (view.data() instanceof SectionContract contract) {
      return new AssetSectionResult(contract.sectionType(), contract.status(), contract.ownerDomain(),
          contract.summary() == null ? new SectionMapSummary(Map.of()) : contract.summary(),
          contract.reason(), contract.updatedAt(), contract.actions(), contract.evidence(),
          contract.provenance(), contract.capability());
    }
    SectionStatus status = view.status();
    String owner = switch (type) {
      case OVERVIEW, GOVERNANCE -> "ASSET";
      case USAGE -> "FEDERATED";
      case TECHNICAL_METADATA -> "METADATA";
      case QUALITY -> "QUALITY";
      case SECURITY -> "SECURITY";
      case LINEAGE -> "LINEAGE";
      case LIFECYCLE -> "LIFECYCLE";
    };
    SectionSummary typedSummary = view.data() instanceof SectionSummary summary ? summary : null;
    Map<String, Object> values = new LinkedHashMap<>();
    if (typedSummary != null) {
      values.putAll(typedSummary.values());
    } else if (view.data() instanceof Map<?, ?> map) {
      map.forEach((key, value) -> values.put(String.valueOf(key), value));
    } else if (view.data() != null) {
      values.put("data", view.data());
    }
    boolean hasReadableResult = status == SectionStatus.OK || status == SectionStatus.EMPTY;
    AssetSectionResult result = new AssetSectionResult(
        type, status, owner, typedSummary == null ? new SectionMapSummary(values) : typedSummary,
        status == SectionStatus.OK ? null : view.note(),
        null, combinedActions(view, type, status, asset), List.of(),
        hasReadableResult ? new SectionProvenance(owner, asset.assetKey(), Instant.now()) : null,
        new SectionCapability(status != SectionStatus.NOT_APPLICABLE,
            status == SectionStatus.OK || status == SectionStatus.EMPTY,
            status == SectionStatus.OK || status == SectionStatus.EMPTY ? null : view.note()));
    return result;
  }

  private static List<SectionAction> combinedActions(
      AssetDiscoverService.SectionView view, SectionType type,
      SectionStatus status, AssetView asset) {
    if (status != SectionStatus.OK) return List.of();
    java.util.ArrayList<SectionAction> actions = new java.util.ArrayList<>(
        sectionActions(type, status, asset));
    actions.addAll(view.actions());
    return List.copyOf(actions);
  }

  private static List<SectionAction> sectionActions(
      SectionType type, SectionStatus status, AssetView asset) {
    if (status != SectionStatus.OK) {
      return List.of();
    }
    String returnContext = "returnAssetId=" + asset.id();
    return switch (type) {
      case TECHNICAL_METADATA -> List.of();
      case LINEAGE -> List.of(new SectionAction(
          "打开全屏血缘图谱",
          "/data-analysis/lineage?assetKey=" + java.net.URLEncoder.encode(
              asset.assetKey(), java.nio.charset.StandardCharsets.UTF_8)
              + "&" + returnContext,
          asset.assetKey()));
      default -> List.of();
    };
  }

}
