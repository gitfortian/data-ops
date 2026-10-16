package io.yak.ops.business.modeling.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.version.ModelVersionService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit DWD design publication only. No default enforcement on legacy publish;
 * no DataDev task, deployment, DDL, or business-grain approval is implied.
 */
@Service
@RequiredArgsConstructor
public class DwdMappingPublicationService {
  private final ModelRepository models;
  private final DwdMappingDraftService mapping;
  private final ModelVersionService versions;
  private final ApprovalApi approvals;
  private final ObjectMapper json;

  public record BoundReceipt(Long modelVersionId, int versionNo, Long mappingRevisionId,
                             boolean created, String state) {}
  public record History(Long modelVersionId, int versionNo, String state,
                        DwdMappingDraftService.HistoricalRevision mapping) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public BoundReceipt publish(Long modelId, Long mappingRevisionId, String operator) {
    return publishChecked(modelId, mappingRevisionId, null, null, operator);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public BoundReceipt publishApproved(Long modelId, Long mappingRevisionId,
                                      String approvedMappingSha, String approvedFingerprint,
                                      String operator) {
    if (approvedFingerprint == null || !approvedFingerprint.matches("[a-f0-9]{64}")) {
      throw new IllegalStateException("审批依据缺失");
    }
    if (approvedMappingSha == null || !approvedMappingSha.matches("[a-f0-9]{64}")) {
      throw new IllegalStateException("审批映射快照指纹缺失");
    }
    return publishChecked(modelId, mappingRevisionId, approvedMappingSha,
        approvedFingerprint, operator);
  }

  private BoundReceipt publishChecked(Long modelId, Long revisionId,
                                      String approvedMappingSha,
                                      String fingerprint, String operator) {
    Model root = requireDwdLock(modelId);
    if (revisionId == null || revisionId <= 0) {
      throw new IllegalArgumentException("必须选择精确 MappingRevision ID");
    }
    // Retries after commit are read-only: no new version and no second approval receipt.
    if (root.status() == ModelStatus.PUBLISHED) {
      History existing = history(modelId, root.latestVersionNo() == null ? 0 : root.latestVersionNo());
      if (existing.mapping() != null
          && Objects.equals(existing.mapping().mappingRevisionId(), revisionId)
          && (approvedMappingSha == null
              || approvedMappingSha.equals(existing.mapping().contentSha256()))
          && (fingerprint == null
              || fingerprint.equals(versions.structureFingerprint(modelId)))) {
        return new BoundReceipt(existing.modelVersionId(), existing.versionNo(),
            revisionId, false, "DESIGN_PUBLISHED_NOT_EXECUTED");
      }
      throw new IllegalStateException("模型已发布；不同映射需要先创建新的物理草稿");
    }
    if (root.status() != ModelStatus.DRAFT) {
      throw new IllegalStateException("只有当前 DWD 草稿可正式发布");
    }
    var exact = mapping.requireFrozenForPublication(modelId, revisionId);
    if (approvedMappingSha != null && !approvedMappingSha.equals(exact.contentSha256())) {
      throw new IllegalStateException("审批映射快照在提交后已变化");
    }
    var result = versions.publishWithMapping(modelId, fingerprint, exact, operator);
    return new BoundReceipt(result.version().id(), result.version().versionNo(),
        exact.mappingRevisionId(), result.created(), "DESIGN_PUBLISHED_NOT_EXECUTED");
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ApprovalInstanceView submitApproval(Long modelId, Long mappingRevisionId,
                                             String operator) {
    Model root = requireDwdLock(modelId);
    if (root.status() != ModelStatus.DRAFT) {
      throw new IllegalStateException("只有 DWD 草稿可提交映射发布审批");
    }
    var exact = mapping.requireFrozenForPublication(modelId, mappingRevisionId);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("modelId", modelId);
    payload.put("modelName", root.name() == null ? "" : root.name());
    payload.put("structureFingerprint", versions.structureFingerprint(modelId));
    payload.put("mappingRevisionId", exact.mappingRevisionId());
    payload.put("mappingContentSha256", exact.contentSha256());
    try {
      return approvals.submit(new ApprovalSubmitCommand(
          ApprovalFlowCodes.MODEL_PUBLISH, "MODEL", String.valueOf(modelId),
          "DWD 映射设计发布申请:" + root.name(),
          json.writeValueAsString(payload), operator));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("审批版本证据无法序列化", ex);
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public History history(Long modelId, int versionNo) {
    Model root = models.findById(modelId)
        .orElseThrow(() -> new IllegalArgumentException("模型不存在或项目不可访问"));
    if (!"DWD".equalsIgnoreCase(root.layerCode())) {
      throw new IllegalArgumentException("此处只查询 DWD 发布绑定");
    }
    var version = versions.getVersionDetail(modelId, versionNo);
    Map<String, Object> meta = version.meta();
    Object revisionId = meta.get("mappingRevisionId");
    if (revisionId == null) {
      return new History(version.version().id(), versionNo, "LEGACY_UNBOUND", null);
    }
    long id;
    try {
      id = Long.parseLong(String.valueOf(revisionId));
    } catch (NumberFormatException ex) {
      throw new IllegalStateException("物理发布历史中的映射引用损坏", ex);
    }
    var historical = mapping.revisionDetail(modelId, id);
    if (!Objects.equals(meta.get("mappingContentSha256"), historical.contentSha256())
        || !Objects.equals(meta.get("logicalSnapshotSha256"), historical.logicalSnapshotSha256())
        || !Objects.equals(meta.get("physicalStructureSha256"), historical.physicalStructureSha256())
        || !String.valueOf(historical.logicalModelId()).equals(String.valueOf(meta.get("logicalModelId")))
        || !String.valueOf(historical.logicalVersionNo()).equals(String.valueOf(meta.get("logicalVersionNo")))) {
      throw new IllegalStateException("物理历史版本与冻结映射证据不一致");
    }
    return new History(version.version().id(), versionNo,
        "DESIGN_PUBLISHED_NOT_EXECUTED", historical);
  }

  private Model requireDwdLock(Long modelId) {
    Model root = models.findByIdForUpdate(modelId)
        .orElseThrow(() -> new IllegalArgumentException("模型不存在或当前项目不可访问"));
    if (!"DWD".equalsIgnoreCase(root.layerCode())) {
      throw new IllegalArgumentException("此发布入口仅用于 DWD");
    }
    return root;
  }
}
