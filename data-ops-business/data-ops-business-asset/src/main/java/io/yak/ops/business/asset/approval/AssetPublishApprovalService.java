package io.yak.ops.business.asset.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.asset.application.AssetLifecycleService;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 资产上架审批发起(M2-5 上架审批事实源):审批中心只存展示快照,台账仍是唯一状态。
 * 同一资产在途单唯一由审批中心 uk 保证(重复发起 → 49003)。
 * 观察期内台账直发上架链路(POST /publish)不关闭,双轨并存、状态条如实区分。
 */
@Service
@RequiredArgsConstructor
public class AssetPublishApprovalService {

  public static final String BIZ_TYPE = "ASSET";
  static final String PAYLOAD_ASSET_ID = "assetId";
  static final String PAYLOAD_ASSET_NAME = "assetName";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ApprovalApi approvalApi;
  private final CurrentProject currentProject;
  private final AssetItemMapper itemMapper;

  public ApprovalInstanceView submit(Long assetId, String operator) {
    AssetItemPO po = itemMapper.selectOne(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, currentProject.requireProjectId())
        .eq(AssetItemPO::getId, assetId)
        .eq(AssetItemPO::getDeleted, false));
    if (po == null) {
      throw new AssetException(AssetErrorCode.ASSET_NOT_FOUND, "id=" + assetId);
    }
    if (!AssetLifecycleService.PUBLISHABLE.contains(po.getStatus())) {
      throw new AssetException(AssetErrorCode.ILLEGAL_STATE_OPERATION,
          "id=" + assetId + " 当前状态 " + po.getStatus() + " 不可发起上架审批");
    }
    String payload;
    try {
      payload = MAPPER.writeValueAsString(Map.of(
          PAYLOAD_ASSET_ID, assetId,
          PAYLOAD_ASSET_NAME, po.getName() == null ? "" : po.getName()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("审批 payload 序列化失败", e);
    }
    return approvalApi.submit(new ApprovalSubmitCommand(
        ApprovalFlowCodes.ASSET_PUBLISH, BIZ_TYPE, String.valueOf(assetId),
        "资产上架申请:" + po.getName(), payload, operator));
  }
}
