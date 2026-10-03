package io.yak.ops.business.asset.application;

import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.asset.api.AssetStatusModelFacts;
import io.yak.ops.business.asset.api.AssetStatusModelFacts.ModelFacts;
import io.yak.ops.business.asset.api.AssetStatusTtlFacts;
import io.yak.ops.business.asset.api.AssetStatusTtlFacts.TtlFacts;
import io.yak.ops.business.asset.approval.AssetPublishApprovalService;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.common.enums.asset.AssetSourceType;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 资产状态条只读聚合(docs/PLATFORM_CORE_FLOW.md M2-1):七格状态 =
 * 已发现→已描述→已定标→已稽核→已上架→运营中→归档策略。
 * 口径:资产域只读取各域既有事实(盖章),不新建写入链路;
 * 依赖缺失或查询失败一律降级 UNKNOWN/NA,不伪造通过。
 */
@Service
@RequiredArgsConstructor
public class AssetStatusFlowService {

  /** result: PASS | FAIL | NA(不适用) | UNKNOWN(事实源暂不可读). */
  public record StatusStep(String key, String title, String result, String note,
      Map<String, Object> facts) {}

  public record StatusFlowView(List<StatusStep> steps, String currentStageKey) {}

  private static final String PASS = "PASS";
  private static final String FAIL = "FAIL";
  private static final String NA = "NA";
  private static final String UNKNOWN = "UNKNOWN";

  private final ObjectProvider<AssetStatusModelFacts> modelFactsApi;
  private final ObjectProvider<AssetStatusTtlFacts> ttlFactsApi;
  private final ObjectProvider<QualityMonitorReader> qualityReader;
  private final ObjectProvider<LineageQueryService> lineageQuery;
  private final ObjectProvider<SecurityClassificationQueryApi> securityQuery;
  private final ObjectProvider<ApprovalApi> approvalApi;

  public StatusFlowView flow(AssetItemPO po) {
    LineageAsset root = lineageRoot(po);
    Optional<ModelFacts> facts = modelFacts(po);
    List<StatusStep> steps = List.of(
        discovered(po, root),
        described(po),
        standardized(facts),
        audited(po, facts),
        listed(po),
        operating(root),
        archivePolicy(po, facts));
    String current = steps.stream()
        .filter(step -> !PASS.equals(step.result()))
        .map(StatusStep::key)
        .findFirst()
        .orElse("COMPLETE");
    return new StatusFlowView(steps, current);
  }

  /** 已发现:源对象存在且已在血缘/元数据域登记;SOURCE_GONE 判失败。 */
  private StatusStep discovered(AssetItemPO po, LineageAsset root) {
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("assetKey", po.getAssetKey());
    if (AssetStatus.SOURCE_GONE.name().equals(po.getStatus())) {
      return step("discovered", "已发现", FAIL, "源对象已消失(对账判定),请先完成变更确认", facts);
    }
    if (AssetSourceType.MANUAL.name().equals(po.getSourceType())) {
      return step("discovered", "已发现", PASS, "手工登记资产,台账即事实", facts);
    }
    return root == null
        ? step("discovered", "已发现", FAIL, "血缘/元数据域尚无该资产登记(源域存在但未落登记)", facts)
        : step("discovered", "已发现", PASS, null, facts);
  }

  /** 已描述:中文名、描述、Owner 三项齐备(与上架 precheck 同口径)。 */
  private StatusStep described(AssetItemPO po) {
    List<String> missing = new ArrayList<>();
    if (!StringUtils.hasText(po.getName())) {
      missing.add("名称");
    }
    if (!StringUtils.hasText(po.getDescription())) {
      missing.add("描述");
    }
    if (!StringUtils.hasText(po.getOwner())) {
      missing.add("Owner");
    }
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("missing", missing);
    return missing.isEmpty()
        ? step("described", "已描述", PASS, null, facts)
        : step("described", "已描述", FAIL, "缺失:" + String.join("、", missing), facts);
  }

  /** 已定标:强制口径由「数仓分层」配置携带(M2-5);强制层要求字段 100% 落标,免强制层直接盖章。 */
  private StatusStep standardized(Optional<ModelFacts> facts) {
    if (facts.isEmpty()) {
      return step("standardized", "已定标", NA, "仅建模来源(TABLE)资产适用,或建模域事实 SPI 未装配",
          Map.of());
    }
    ModelFacts mf = facts.get();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("layerCode", mf.layerCode());
    data.put("columnTotal", mf.columnTotal());
    data.put("stdBoundColumns", mf.stdBoundColumns());
    if (!StringUtils.hasText(mf.layerCode())) {
      return step("standardized", "已定标", FAIL, "模型未挂数仓分层,无法判定强制口径", data);
    }
    if (mf.stdMandatory() == null) {
      return step("standardized", "已定标", FAIL,
          "分层 " + mf.layerCode() + " 未在「数仓分层」配置登记,无法判定强制口径", data);
    }
    data.put("stdMandatory", mf.stdMandatory());
    if (!mf.stdMandatory()) {
      return step("standardized", "已定标", PASS, "分层 " + mf.layerCode() + " 配置为免强制", data);
    }
    if (mf.columnTotal() <= 0) {
      return step("standardized", "已定标", FAIL, "模型尚无字段", data);
    }
    return mf.stdBoundColumns() >= mf.columnTotal()
        ? step("standardized", "已定标", PASS, null, data)
        : step("standardized", "已定标", FAIL,
            "字段落标 " + mf.stdBoundColumns() + "/" + mf.columnTotal(), data);
  }

  /** 已稽核:质量监控最近一次执行通过,且安全分级已完成。 */
  private StatusStep audited(AssetItemPO po, Optional<ModelFacts> facts) {
    if (facts.isEmpty()) {
      return step("audited", "已稽核", NA, "无物理落点信息(非建模 TABLE 资产或 SPI 未装配)",
          Map.of());
    }
    ModelFacts mf = facts.get();
    if (mf.datasourceId() == null || !StringUtils.hasText(mf.databaseName())) {
      return step("audited", "已稽核", UNKNOWN,
          "分层未配置数据源/库,无法定位质量监控四元组", Map.of());
    }
    QualityMonitorReader reader = qualityReader.getIfAvailable();
    if (reader == null) {
      return step("audited", "已稽核", UNKNOWN, "质量域未装配", Map.of());
    }
    Map<String, Object> data = new LinkedHashMap<>();
    TableMonitorSummary summary;
    try {
      summary = reader.tableSummaries(mf.datasourceId(), mf.databaseName(), mf.schemaName())
          .stream()
          .filter(s -> mf.tableName().equalsIgnoreCase(s.tableName()))
          .findFirst()
          .orElse(null);
    } catch (RuntimeException e) {
      return step("audited", "已稽核", UNKNOWN, "质量查询失败: " + e.getMessage(), Map.of());
    }
    if (summary == null) {
      return step("audited", "已稽核", FAIL, "该表未挂质量监控", data);
    }
    data.put("monitorId", summary.monitorId());
    data.put("monitorCount", summary.monitorCount());
    data.put("lastResult", summary.lastResult() == null ? null : summary.lastResult().name());
    data.put("lastRunTime", summary.lastRunTime());
    if (summary.lastResult() != CheckResult.PASSED) {
      String label = summary.lastResult() == null ? "未运行" : summary.lastResult().name();
      return step("audited", "已稽核", FAIL, "质量最近执行:" + label, data);
    }
    boolean classified = classified(po, mf);
    data.put("securityLevelCode", po.getSecurityLevelCode());
    return classified
        ? step("audited", "已稽核", PASS, null, data)
        : step("audited", "已稽核", FAIL, "质量通过,但安全分级未完成", data);
  }

  private boolean classified(AssetItemPO po, ModelFacts mf) {
    if (StringUtils.hasText(po.getSecurityLevelCode())) {
      return true;
    }
    SecurityClassificationQueryApi api = securityQuery.getIfAvailable();
    if (api == null) {
      return false;
    }
    try {
      return !api.findByTable(mf.databaseName(), mf.tableName()).isEmpty();
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * 已上架:台账 PUBLISHED 仍是落库事实;M2-5 接入审批后优先展示审批事实
   * (审批通过单 → "审批通过后上架")。观察期直发链路未关,无审批记录的 PUBLISHED
   * 如实标"台账直接上架",不伪造审批。
   */
  private StatusStep listed(AssetItemPO po) {
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("status", po.getStatus());
    facts.put("firstListedAt", po.getFirstListedAt());
    facts.put("lastListedAt", po.getLastListedAt());
    ApprovalApi api = approvalApi.getIfAvailable();
    ApprovalInstanceView instance = approvalFact(api, po);
    if (instance != null) {
      facts.put("approval", "#" + instance.id() + " " + instance.status());
    }
    AssetStatus status = parseStatus(po.getStatus());
    return switch (status) {
      case PUBLISHED -> instance != null && "APPROVED".equals(instance.status())
          ? step("listed", "已上架", PASS, "审批通过后上架(单 #" + instance.id() + ")", facts)
          : step("listed", "已上架", PASS, api == null
              ? "审批域未装配,按台账上架事实代打" : "台账直接上架(无审批通过记录)", facts);
      case PENDING -> step("listed", "已上架", FAIL,
          instance != null && "PENDING".equals(instance.status())
              ? "上架审批在途(单 #" + instance.id() + "),台账尚未上架" : "尚未上架(盘点池)", facts);
      case OFFLINE -> step("listed", "已上架", FAIL, "已下架"
          + (StringUtils.hasText(po.getLastOfflineReason()) ? ":" + po.getLastOfflineReason() : ""), facts);
      case IGNORED -> step("listed", "已上架", FAIL, "被忽略,不在账本流转内", facts);
      case SOURCE_GONE -> step("listed", "已上架", FAIL, "源已消失", facts);
    };
  }

  /** 审批事实只读:在途单优先,否则最近一单;未装配/查询失败返回 null(不判失败)。 */
  private static ApprovalInstanceView approvalFact(ApprovalApi api, AssetItemPO po) {
    if (api == null) {
      return null;
    }
    try {
      return api.find(ApprovalFlowCodes.ASSET_PUBLISH, AssetPublishApprovalService.BIZ_TYPE,
          String.valueOf(po.getId()));
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** 运营中:血缘 1 跳下游存在消费引用。 */
  private StatusStep operating(LineageAsset root) {
    if (root == null) {
      return step("operating", "运营中", UNKNOWN, "血缘未登记,无法判定下游消费", Map.of());
    }
    LineageQueryService service = lineageQuery.getIfAvailable();
    if (service == null) {
      return step("operating", "运营中", UNKNOWN, "血缘服务未装配", Map.of());
    }
    Map<String, Object> facts = new LinkedHashMap<>();
    try {
      int downstream = service.graph(root.id(), LineageDirection.DOWNSTREAM, 1).relations().size();
      facts.put("downstreamCount", downstream);
      return downstream > 0
          ? step("operating", "运营中", PASS, null, facts)
          : step("operating", "运营中", FAIL, "暂无下游消费引用", facts);
    } catch (RuntimeException e) {
      return step("operating", "运营中", UNKNOWN, "血缘查询失败: " + e.getMessage(), Map.of());
    }
  }

  /** 归档策略:命中 TTL 策略(绑定或分层默认)即盖章;状态取 D5 下发态。 */
  private StatusStep archivePolicy(AssetItemPO po, Optional<ModelFacts> facts) {
    if (!AssetSourceType.MODEL.name().equals(po.getSourceType())) {
      return step("archivePolicy", "归档策略", NA, "TTL 目前仅锚定建模模型资产", Map.of());
    }
    AssetStatusTtlFacts api = ttlFactsApi.getIfAvailable();
    if (api == null) {
      return step("archivePolicy", "归档策略", UNKNOWN, "生命周期 SPI 未装配", Map.of());
    }
    Optional<TtlFacts> ttl;
    try {
      ttl = api.ttlFacts(po.getSourceId());
    } catch (RuntimeException e) {
      return step("archivePolicy", "归档策略", UNKNOWN, "TTL 查询失败: " + e.getMessage(),
          Map.of());
    }
    if (ttl.isEmpty()) {
      return step("archivePolicy", "归档策略", UNKNOWN, "模型在生命周期域不可解析", Map.of());
    }
    TtlFacts t = ttl.get();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("policyCode", t.policyCode());
    data.put("bindingSource", t.bindingSource());
    data.put("state", t.state());
    if (!t.policyApplied()) {
      return step("archivePolicy", "归档策略", FAIL,
          "未绑定 TTL 策略(分层默认亦未配置),数据无退出通道", data);
    }
    String suffix = facts.map(mf -> mf.hasTimePartition() ? "" : ";模型无时间分区,TTL 无法下发")
        .orElse("");
    return step("archivePolicy", "归档策略", PASS,
        "策略 " + t.policyCode() + ",下发状态 " + t.state() + suffix, data);
  }

  private LineageAsset lineageRoot(AssetItemPO po) {
    LineageQueryService service = lineageQuery.getIfAvailable();
    if (service == null) {
      return null;
    }
    try {
      return service.getAssetByKey(po.getAssetKey());
    } catch (RuntimeException e) {
      return null;
    }
  }

  private Optional<ModelFacts> modelFacts(AssetItemPO po) {
    if (!AssetSourceType.MODEL.name().equals(po.getSourceType())) {
      return Optional.empty();
    }
    AssetStatusModelFacts api = modelFactsApi.getIfAvailable();
    if (api == null) {
      return Optional.empty();
    }
    try {
      return api.modelFacts(po.getSourceId());
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  private static AssetStatus parseStatus(String raw) {
    try {
      return AssetStatus.valueOf(raw);
    } catch (RuntimeException e) {
      return AssetStatus.PENDING;
    }
  }

  private static StatusStep step(String key, String title, String result, String note,
      Map<String, Object> facts) {
    return new StatusStep(key, title, result, note, facts);
  }
}
