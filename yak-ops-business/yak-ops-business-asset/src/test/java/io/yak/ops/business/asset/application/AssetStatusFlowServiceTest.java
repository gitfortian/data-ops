package io.yak.ops.business.asset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.asset.api.AssetStatusModelFacts;
import io.yak.ops.business.asset.api.AssetStatusModelFacts.ModelFacts;
import io.yak.ops.business.asset.api.AssetStatusTtlFacts;
import io.yak.ops.business.asset.api.AssetStatusTtlFacts.TtlFacts;
import io.yak.ops.business.asset.application.AssetStatusFlowService.StatusFlowView;
import io.yak.ops.business.asset.application.AssetStatusFlowService.StatusStep;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.common.enums.asset.AssetSourceType;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** M2-1 状态条聚合单测:七格口径、缺口降级为 UNKNOWN/NA、绝不伪造 PASS。 */
class AssetStatusFlowServiceTest {

  private ObjectProvider<AssetStatusModelFacts> modelFactsProvider;
  private ObjectProvider<AssetStatusTtlFacts> ttlFactsProvider;
  private ObjectProvider<QualityMonitorReader> qualityProvider;
  private ObjectProvider<LineageQueryService> lineageProvider;
  private ObjectProvider<SecurityClassificationQueryApi> securityProvider;
  private ObjectProvider<ApprovalApi> approvalProvider;
  private AssetStatusFlowService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    modelFactsProvider = mock(ObjectProvider.class);
    ttlFactsProvider = mock(ObjectProvider.class);
    qualityProvider = mock(ObjectProvider.class);
    lineageProvider = mock(ObjectProvider.class);
    securityProvider = mock(ObjectProvider.class);
    approvalProvider = mock(ObjectProvider.class);
    service = new AssetStatusFlowService(modelFactsProvider, ttlFactsProvider, qualityProvider,
        lineageProvider, securityProvider, approvalProvider);
  }

  @Test
  void allFactsStampedReachesComplete() {
    AssetItemPO po = modelItem();
    stubModelFacts(Optional.of(modelFacts("DWD", 5, 5)));
    stubTtl(Optional.of(new TtlFacts(true, "TTL-30D", "LAYER_DEFAULT", "APPLIED")));
    stubQuality(CheckResult.PASSED);
    stubLineage(true);

    StatusFlowView view = service.flow(po);

    assertEquals(List.of("discovered", "described", "standardized", "audited", "listed",
        "operating", "archivePolicy"), view.steps().stream().map(StatusStep::key).toList());
    assertEquals("COMPLETE", view.currentStageKey());
  }

  @Test
  void layerConfiguredExemptFromStandardFieldBinding() {
    stubModelFacts(Optional.of(modelFacts("ODS", false, 8, 0)));

    StatusStep step = stepOf(service.flow(modelItem()), "standardized");

    assertEquals("PASS", step.result());
    assertEquals("分层 ODS 配置为免强制", step.note());
  }

  @Test
  void unregisteredLayerCodeFailsLoudlyNotExempt() {
    stubModelFacts(Optional.of(modelFacts("MART", null, 8, 8)));

    StatusStep step = stepOf(service.flow(modelItem()), "standardized");

    assertEquals("FAIL", step.result());
    assertEquals("分层 MART 未在「数仓分层」配置登记,无法判定强制口径", step.note());
  }

  @Test
  void processingLayerPartialBindingFailsWithRatio() {
    stubModelFacts(Optional.of(modelFacts("DWS", 10, 4)));

    StatusStep step = stepOf(service.flow(modelItem()), "standardized");

    assertEquals("FAIL", step.result());
    assertEquals("字段落标 4/10", step.note());
  }

  @Test
  void missingExtensionsDegradeAndNeverFakePass() {
    StatusFlowView view = service.flow(modelItem());

    assertEquals("FAIL", stepOf(view, "discovered").result());
    assertEquals("NA", stepOf(view, "standardized").result());
    assertEquals("NA", stepOf(view, "audited").result());
    assertEquals("UNKNOWN", stepOf(view, "operating").result());
    assertEquals("UNKNOWN", stepOf(view, "archivePolicy").result());
    assertEquals("discovered", view.currentStageKey());
  }

  @Test
  void qualityPassedButUnclassifiedFailsAudited() {
    AssetItemPO po = modelItem();
    po.setSecurityLevelCode(null);
    stubModelFacts(Optional.of(modelFacts("DWD", 5, 5)));
    stubQuality(CheckResult.PASSED);
    SecurityClassificationQueryApi security = mock(SecurityClassificationQueryApi.class);
    when(security.findByTable(anyString(), anyString())).thenReturn(List.of());
    when(securityProvider.getIfAvailable()).thenReturn(security);

    StatusStep step = stepOf(service.flow(po), "audited");

    assertEquals("FAIL", step.result());
    assertEquals("质量通过,但安全分级未完成", step.note());
  }

  @Test
  void factSpiFailureDegradesToNaOrUnknownNotFail() {
    AssetStatusModelFacts brokenFacts = mock(AssetStatusModelFacts.class);
    when(brokenFacts.modelFacts(anyString())).thenThrow(new RuntimeException("modeling down"));
    when(modelFactsProvider.getIfAvailable()).thenReturn(brokenFacts);
    AssetStatusTtlFacts brokenTtl = mock(AssetStatusTtlFacts.class);
    when(brokenTtl.ttlFacts(anyString())).thenThrow(new RuntimeException("lifecycle down"));
    when(ttlFactsProvider.getIfAvailable()).thenReturn(brokenTtl);

    StatusFlowView view = service.flow(modelItem());

    assertEquals("NA", stepOf(view, "standardized").result());
    assertEquals("UNKNOWN", stepOf(view, "archivePolicy").result());
  }

  @Test
  void manualAssetSkipsModelOnlyStages() {
    AssetItemPO po = modelItem();
    po.setSourceType(AssetSourceType.MANUAL.name());

    StatusFlowView view = service.flow(po);

    assertEquals("PASS", stepOf(view, "discovered").result());
    assertEquals("NA", stepOf(view, "standardized").result());
    assertEquals("NA", stepOf(view, "archivePolicy").result());
  }

  @Test
  void sourceGoneFailsDiscovered() {
    AssetItemPO po = modelItem();
    po.setStatus(AssetStatus.SOURCE_GONE.name());

    StatusStep step = stepOf(service.flow(po), "discovered");

    assertEquals("FAIL", step.result());
    assertEquals("discovered", service.flow(po).currentStageKey());
  }

  @Test
  void publishedWithApprovedInstanceReportsApprovalFact() {
    AssetItemPO po = modelItem();
    ApprovalApi approval = mock(ApprovalApi.class);
    when(approval.find(ApprovalFlowCodes.ASSET_PUBLISH, "ASSET", "1"))
        .thenReturn(approvalInstance(7L, "APPROVED"));
    when(approvalProvider.getIfAvailable()).thenReturn(approval);

    StatusStep step = stepOf(service.flow(po), "listed");

    assertEquals("PASS", step.result());
    assertEquals("审批通过后上架(单 #7)", step.note());
  }

  @Test
  void publishedWithoutApprovalHistoryStaysHonest() {
    AssetItemPO po = modelItem();
    ApprovalApi approval = mock(ApprovalApi.class);
    when(approval.find(anyString(), anyString(), anyString())).thenReturn(null);
    when(approvalProvider.getIfAvailable()).thenReturn(approval);

    StatusStep step = stepOf(service.flow(po), "listed");

    assertEquals("PASS", step.result());
    assertEquals("台账直接上架(无审批通过记录)", step.note());
  }

  @Test
  void inFlightApprovalShownOnPendingAsset() {
    AssetItemPO po = modelItem();
    po.setStatus(AssetStatus.PENDING.name());
    ApprovalApi approval = mock(ApprovalApi.class);
    when(approval.find(ApprovalFlowCodes.ASSET_PUBLISH, "ASSET", "1"))
        .thenReturn(approvalInstance(8L, "PENDING"));
    when(approvalProvider.getIfAvailable()).thenReturn(approval);

    StatusStep step = stepOf(service.flow(po), "listed");

    assertEquals("FAIL", step.result());
    assertEquals("上架审批在途(单 #8),台账尚未上架", step.note());
  }

  // ---------- helpers ----------

  private static ApprovalInstanceView approvalInstance(long id, String status) {
    return new ApprovalInstanceView(id, ApprovalFlowCodes.ASSET_PUBLISH, "资产上架审批", "ASSET",
        "1", "t", null, "tom", status, 1, LocalDateTime.now(), null);
  }

  private static AssetItemPO modelItem() {
    AssetItemPO po = new AssetItemPO();
    po.setId(1L);
    po.setProjectId(1L);
    po.setAssetKey("modeling:model:42");
    po.setSourceType(AssetSourceType.MODEL.name());
    po.setSourceId("42");
    po.setAssetType(AssetType.TABLE.name());
    po.setName("订单模型");
    po.setDescription("dwd 订单");
    po.setOwner("alice");
    po.setStatus(AssetStatus.PUBLISHED.name());
    po.setSecurityLevelCode("L3");
    po.setDeleted(false);
    return po;
  }

  private static ModelFacts modelFacts(String layerCode, long columnTotal, long stdBound) {
    return modelFacts(layerCode, true, columnTotal, stdBound);
  }

  private static ModelFacts modelFacts(
      String layerCode, Boolean stdMandatory, long columnTotal, long stdBound) {
    return new ModelFacts(layerCode, stdMandatory, 9L, "dw", null, "t_order", columnTotal,
        stdBound, "EFFECTIVE", true);
  }

  private void stubModelFacts(Optional<ModelFacts> facts) {
    AssetStatusModelFacts api = mock(AssetStatusModelFacts.class);
    when(api.modelFacts("42")).thenReturn(facts);
    when(modelFactsProvider.getIfAvailable()).thenReturn(api);
  }

  private void stubTtl(Optional<TtlFacts> ttl) {
    AssetStatusTtlFacts api = mock(AssetStatusTtlFacts.class);
    when(api.ttlFacts("42")).thenReturn(ttl);
    when(ttlFactsProvider.getIfAvailable()).thenReturn(api);
  }

  private void stubQuality(CheckResult lastResult) {
    QualityMonitorReader reader = mock(QualityMonitorReader.class);
    when(reader.tableSummaries(anyLong(), anyString(), any())).thenReturn(List.of(
        new TableMonitorSummary("T_ORDER", 3L, "订单监控", 1, 4, lastResult, LocalDateTime.now())));
    when(qualityProvider.getIfAvailable()).thenReturn(reader);
  }

  @SuppressWarnings("unchecked")
  private void stubLineage(boolean withDownstream) {
    LineageQueryService lineage = mock(LineageQueryService.class);
    LineageAsset root = mock(LineageAsset.class);
    when(root.id()).thenReturn(7L);
    when(lineage.getAssetByKey("modeling:model:42")).thenReturn(root);
    List<LineageRelation> relations =
        withDownstream ? Arrays.asList((LineageRelation) null) : List.of();
    when(lineage.graph(7L, LineageDirection.DOWNSTREAM, 1))
        .thenReturn(new LineageGraph(root, LineageDirection.DOWNSTREAM, 1, List.of(), relations));
    when(lineageProvider.getIfAvailable()).thenReturn(lineage);
  }

  private static StatusStep stepOf(StatusFlowView view, String key) {
    return view.steps().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
  }
}
