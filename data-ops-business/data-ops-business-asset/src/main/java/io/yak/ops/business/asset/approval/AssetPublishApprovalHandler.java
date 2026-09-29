package io.yak.ops.business.asset.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalFlowHandler;
import io.yak.ops.business.asset.application.AssetLifecycleService;
import io.yak.ops.business.asset.application.PrecheckTokenService;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ASSET_PUBLISH 终态回调(onApproved = 业务生效点,与审批同事务 D5):
 * 批准后仍走既有 publish 链路上架 —— 预检 token 由服务端自签,但缺口
 * (负责人/描述/目录)依旧阻断:抛错 → 49009 整体回滚,补数据后审批人重试。
 * 只向下依赖 asset 内服务,不回依赖 ApprovalApi(防 bean 循环,同 mdm 分层)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetPublishApprovalHandler implements ApprovalFlowHandler {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CurrentProject currentProject;
  private final PrecheckTokenService tokenService;
  private final AssetLifecycleService lifecycleService;

  @Override
  public String flowCode() {
    return ApprovalFlowCodes.ASSET_PUBLISH;
  }

  @Override
  public void onApproved(ApprovalDecision decision) {
    long assetId = readAssetId(decision.payloadJson());
    List<Long> assetIds = List.of(assetId);
    String token = tokenService.issue(currentProject.requireProjectId(), assetIds);
    int published = lifecycleService.publish(assetIds, token, false, decision.applicant());
    log.info("资产上架审批通过即上架: assetId={}, published={}, applicant={}",
        assetId, published, decision.applicant());
  }

  private static long readAssetId(String payloadJson) {
    try {
      JsonNode node = MAPPER.readTree(payloadJson)
          .path(AssetPublishApprovalService.PAYLOAD_ASSET_ID);
      if (!node.canConvertToLong()) {
        throw new IllegalStateException("payload 缺少 assetId: " + payloadJson);
      }
      return node.asLong();
    } catch (Exception e) {
      throw new IllegalStateException("审批 payload 解析失败:" + e.getMessage(), e);
    }
  }
}
