package io.yak.ops.business.modeling.mapping;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.modeling.dao.mapper.ModelingMappingRevisionMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingMappingSetMapper;
import io.yak.ops.business.modeling.dao.model.ModelingMappingRevisionPO;
import io.yak.ops.business.modeling.dao.model.ModelingMappingSetPO;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.logical.LogicalPhysicalHandoffPreviewService;
import io.yak.ops.business.modeling.logical.LogicalPhysicalHandoffReviewService;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * R12 stage 1: a persisted, explicitly human-selected DWD mapping.
 * Freezing only saves immutable DESIGN EVIDENCE; it does not approve business
 * grain, publish a physical model, execute SQL, or authorize DataDev.
 */
@Service
@RequiredArgsConstructor
public class DwdMappingDraftService {
  private final CurrentProject currentProject;
  private final ModelRepository models;
  private final ApprovalApi approvals;
  private final ModelingMappingSetMapper sets;
  private final ModelingMappingRevisionMapper revisions;
  private final LogicalPhysicalHandoffPreviewService previews;
  private final LogicalPhysicalHandoffReviewService reviews;
  private final ObjectMapper json;

  public record RuleInput(Long physicalColumnId, Long logicalAttributeId, String kind) {}
  /** No projectId from browser; trusted current Project is always authoritative. */
  public record SaveRequest(Long logicalModelId, int logicalVersionNo,
                            String logicalSnapshotSha256, String physicalStructureSha256,
                            Long expectedDraftRevision, List<RuleInput> rules) {}
  /** Stored rules name the target column; unstable physical row IDs are never persisted. */
  public record StoredRule(String targetColumn, Long logicalAttributeId,
                           Long stdFieldId, String kind) {}
  public record Draft(Long mappingSetId, Long physicalModelId, Long logicalModelId,
                      int logicalVersionNo, String logicalSnapshotSha256,
                      String physicalStructureSha256, long draftRevision,
                      List<StoredRule> rules, String state) {}
  public record Frozen(Long mappingRevisionId, Long mappingSetId, int revisionNo,
                       String contentSha256, long draftRevision,
                       String state) {}

  /** DWD only; DIM requires its separate entity/SCD identity contract. */
  private Model requireDwd(Long physicalId) {
    if (physicalId == null || physicalId <= 0) {
      throw new IllegalArgumentException("必须指定当前项目内的 DWD 物理模型");
    }
    Model m = models.findById(physicalId)
        .orElseThrow(() -> new IllegalArgumentException("物理模型不存在或当前项目无权访问"));
    if (!"DWD".equalsIgnoreCase(m.layerCode())) {
      throw new IllegalArgumentException("R12 初版仅支持 DWD 正式映射草稿");
    }
    return m;
  }

  private ModelingMappingSetPO current(Long physicalId, boolean lock) {
    var q = new LambdaQueryWrapper<ModelingMappingSetPO>()
        .eq(ModelingMappingSetPO::getProjectId, currentProject.requireProjectId())
        .eq(ModelingMappingSetPO::getPhysicalModelId, physicalId);
    if (lock) q.last("FOR UPDATE");
    return sets.selectOne(q);
  }

  private static boolean sha(String value) {
    return value != null && value.matches("[a-fA-F0-9]{64}");
  }

  private List<StoredRule> check(SaveRequest request, Long physicalId) {
    if (request == null || request.logicalModelId() == null || request.logicalModelId() <= 0
        || request.logicalVersionNo() <= 0 || !sha(request.logicalSnapshotSha256())
        || !sha(request.physicalStructureSha256())
        || request.rules() == null || request.rules().isEmpty()
        || request.rules().size() > 1000) {
      throw new IllegalArgumentException("映射规则、模型版本或冻结指纹非法");
    }
    Set<Long> columns = new HashSet<>();
    Set<Long> attrs = new HashSet<>();
    for (RuleInput r : request.rules()) {
      if (r == null || r.physicalColumnId() == null || r.logicalAttributeId() == null
          || r.physicalColumnId() <= 0 || r.logicalAttributeId() <= 0
          || r.kind() == null || !Set.of("DIRECT", "RENAME").contains(r.kind())
          || !columns.add(r.physicalColumnId())
          || !attrs.add(r.logicalAttributeId())) {
        throw new IllegalArgumentException("DWD 初版仅允许无重复的一对一 DIRECT/RENAME 映射");
      }
    }
    var result = reviews.validate(request.logicalModelId(), request.logicalVersionNo(),
        physicalId, new LogicalPhysicalHandoffReviewService.ReviewRequest(
            request.logicalSnapshotSha256(), request.physicalStructureSha256(),
            request.rules().stream()
                .map(r -> new LogicalPhysicalHandoffReviewService.Selection(
                    r.logicalAttributeId(), r.physicalColumnId()))
                .toList()));
    if (!result.readyForManualDesign() || !"REVIEWABLE_ONLY".equals(result.status())
        || !Objects.equals(result.logicalSnapshotSha256(), request.logicalSnapshotSha256())
        || !Objects.equals(result.physicalStructureSha256(), request.physicalStructureSha256())
        || result.reviewedMappings().size() != request.rules().size()) {
      throw new IllegalStateException("当前项目逻辑/物理版本或标准证据未通过重检，不能保存正式映射");
    }
    List<StoredRule> output = new ArrayList<>();
    for (var row : result.reviewedMappings()) {
      RuleInput rule = request.rules().stream()
          .filter(r -> Objects.equals(r.physicalColumnId(), row.physicalColumnId()))
          .findFirst().orElseThrow();
      if (row.physicalColumn() == null || row.physicalColumn().isBlank()
          || row.stdFieldId() == null) {
        throw new IllegalStateException("目标物理列或标准字段身份缺失");
      }
      output.add(new StoredRule(row.physicalColumn(), row.logicalAttributeId(),
          row.stdFieldId(), rule.kind()));
    }
    return List.copyOf(output);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Draft get(Long physicalId) {
    requireDwd(physicalId);
    ModelingMappingSetPO row = current(physicalId, false);
    if (row == null) return null;
    return view(row);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Draft save(Long physicalId, SaveRequest request, String actor) {
    Model model = requireDwd(physicalId);
    rejectIfPublicationApprovalPending(physicalId);
    if (model.status() != ModelStatus.DRAFT) {
      throw new IllegalStateException("物理模型已发布，请显式建立新的 DWD 草稿版本后再映射");
    }
    // Lock the physical root before validating the live column IDs; ordinary
    // structure saves lock the same root. Prevents check-then-replace races.
    Model locked = models.findByIdForUpdate(physicalId)
        .orElseThrow(() -> new IllegalStateException("物理模型已变更或项目权限已失效"));
    if (locked.status() != ModelStatus.DRAFT || !"DWD".equalsIgnoreCase(locked.layerCode())) {
      throw new IllegalStateException("物理模型在认领前已变更，不允许保存旧映射");
    }
    List<StoredRule> rules = check(request, physicalId);
    ModelingMappingSetPO row = current(physicalId, true);
    long expected = request.expectedDraftRevision() == null ? -1 : request.expectedDraftRevision();
    if (row == null && expected != 0) {
      throw new IllegalStateException("MappingSet 尚未创建，首次保存 expectedDraftRevision 必须为 0");
    }
    if (row != null && (!Objects.equals(row.getDraftRevision(), expected)
        || !Objects.equals(row.getLogicalModelId(), request.logicalModelId())
        || !Objects.equals(row.getLogicalVersionNo(), request.logicalVersionNo())
        || !Objects.equals(row.getLogicalSnapshotSha(), request.logicalSnapshotSha256()))) {
      throw new IllegalStateException("MappingSet 版本已变化，或逻辑来源不一致，请刷新后重新审阅");
    }
    if (row == null) {
      row = new ModelingMappingSetPO();
      row.setProjectId(currentProject.requireProjectId());
      row.setPhysicalModelId(physicalId);
      row.setLogicalModelId(request.logicalModelId());
      row.setLogicalVersionNo(request.logicalVersionNo());
      row.setLogicalSnapshotSha(request.logicalSnapshotSha256());
      row.setDraftRevision(1L);
    } else {
      row.setDraftRevision(row.getDraftRevision() + 1);
    }
    row.setPhysicalStructureSha(request.physicalStructureSha256());
    row.setMappingJson(writeJson(rules));
    row.setUpdatedBy(actor == null ? "unknown" : actor);
    row.setUpdatedAt(LocalDateTime.now());
    if (row.getId() == null) {
      if (sets.insert(row) != 1) throw new IllegalStateException("MappingSet 首次保存失败");
    } else if (sets.updateById(row) != 1) {
      throw new IllegalStateException("MappingSet 并发更新失败");
    }
    return view(row);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Frozen freeze(Long physicalId, Long expectedDraftRevision, String actor) {
    Model model = requireDwd(physicalId);
    rejectIfPublicationApprovalPending(physicalId);
    if (model.status() != ModelStatus.DRAFT) {
      throw new IllegalStateException("只有 DWD 物理草稿可冻结映射");
    }
    Model locked = models.findByIdForUpdate(physicalId)
        .orElseThrow(() -> new IllegalStateException("物理模型不存在或项目权限已撤销"));
    if (locked.status() != ModelStatus.DRAFT || !"DWD".equalsIgnoreCase(locked.layerCode())) {
      throw new IllegalStateException("物理模型在冻结前已发生状态变化");
    }
    ModelingMappingSetPO row = current(physicalId, true);
    if (row == null || expectedDraftRevision == null
        || !Objects.equals(row.getDraftRevision(), expectedDraftRevision)) {
      throw new IllegalStateException("映射草稿修订已变化，请刷新后再冻结");
    }
    List<StoredRule> saved = readRules(row.getMappingJson());
    // Column database row IDs are transient. Resolve names against an exact,
    // freshly authorized version-scoped preview rather than persisting row IDs.
    var preview = previews.preview(row.getLogicalModelId(), row.getLogicalVersionNo(), physicalId);
    if (!Objects.equals(preview.logicalSnapshotSha256(), row.getLogicalSnapshotSha())
        || !Objects.equals(preview.physicalStructureSha256(), row.getPhysicalStructureSha())) {
      throw new IllegalStateException("逻辑快照或物理结构发生漂移，不可冻结旧映射");
    }
    List<RuleInput> currentSelections = new ArrayList<>();
    for (StoredRule rule : saved) {
      var column = preview.columns().stream()
          .filter(c -> Objects.equals(c.physicalColumn(), rule.targetColumn())
              && Objects.equals(c.physicalStdFieldId(), rule.stdFieldId())
              && c.physicalColumnId() != null)
          .toList();
      if (column.size() != 1) {
        throw new IllegalStateException("冻结目标列已被重命名或标准身份发生变化");
      }
      currentSelections.add(new RuleInput(column.get(0).physicalColumnId(),
          rule.logicalAttributeId(), rule.kind()));
    }
    List<StoredRule> refreshed = check(new SaveRequest(
        row.getLogicalModelId(), row.getLogicalVersionNo(),
        row.getLogicalSnapshotSha(), row.getPhysicalStructureSha(),
        row.getDraftRevision(), currentSelections), physicalId);
    if (!saved.equals(refreshed)) {
      throw new IllegalStateException("来源证据发生变化，需重新保存映射草稿");
    }
    var latest = revisions.selectList(new LambdaQueryWrapper<ModelingMappingRevisionPO>()
        .eq(ModelingMappingRevisionPO::getProjectId, currentProject.requireProjectId())
        .eq(ModelingMappingRevisionPO::getMappingSetId, row.getId())
        .orderByDesc(ModelingMappingRevisionPO::getRevisionNo)
        .last("LIMIT 1"));
    String frozenJson = writeJson(saved);
    String contentSha = VersionDigests.sha256Hex(
        row.getLogicalSnapshotSha() + ":" + row.getPhysicalStructureSha() + ":" + frozenJson);
    // A repeated freeze command for the SAME persisted draft is a readback,
    // never an extra immutable version. The root lock also serializes workers.
    if (!latest.isEmpty()
        && Objects.equals(latest.get(0).getDraftRevision(), row.getDraftRevision())
        && Objects.equals(latest.get(0).getContentSha(), contentSha)) {
      return frozen(latest.get(0));
    }
    int nextNo = latest.isEmpty() ? 1 : latest.get(0).getRevisionNo() + 1;
    ModelingMappingRevisionPO version = new ModelingMappingRevisionPO();
    version.setProjectId(currentProject.requireProjectId());
    version.setMappingSetId(row.getId());
    version.setRevisionNo(nextNo);
    version.setDraftRevision(row.getDraftRevision());
    version.setLogicalSnapshotSha(row.getLogicalSnapshotSha());
    version.setPhysicalStructureSha(row.getPhysicalStructureSha());
    version.setFrozenJson(frozenJson);
    version.setContentSha(contentSha);
    version.setFrozenBy(actor == null ? "unknown" : actor);
    version.setFrozenAt(LocalDateTime.now());
    if (revisions.insert(version) != 1) throw new IllegalStateException("映射修订冻结失败");
    return frozen(version);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public List<Frozen> revisions(Long physicalId) {
    requireDwd(physicalId);
    ModelingMappingSetPO row = current(physicalId, false);
    if (row == null) return List.of();
    return revisions.selectList(new LambdaQueryWrapper<ModelingMappingRevisionPO>()
        .eq(ModelingMappingRevisionPO::getProjectId, currentProject.requireProjectId())
        .eq(ModelingMappingRevisionPO::getMappingSetId, row.getId())
        .orderByDesc(ModelingMappingRevisionPO::getRevisionNo)
        .last("LIMIT 100")).stream().map(this::frozen).toList();
  }


  /** Editing a design under approval would invalidate the pinned approval evidence. */
  private void rejectIfPublicationApprovalPending(Long physicalId) {
    var pending = approvals.find(ApprovalFlowCodes.MODEL_PUBLISH, "MODEL",
        String.valueOf(physicalId));
    if (pending != null && "PENDING".equals(pending.status())) {
      throw new IllegalStateException("DWD 设计发布审批进行中，不能修改映射；请等待审批结束后再编辑");
    }
  }

  /** Exact frozen evidence, revalidated before an explicit DWD publish transaction. */
  public record PublicationEvidence(
      Long mappingRevisionId, Long mappingSetId, Long logicalModelId,
      int logicalVersionNo, String logicalSnapshotSha256,
      String physicalStructureSha256, String contentSha256) {}

  /** Historical mapping is immutable. It is NEVER validated against today's standards. */
  public record HistoricalRevision(
      Long mappingRevisionId, Long mappingSetId, int revisionNo,
      Long logicalModelId, int logicalVersionNo,
      String logicalSnapshotSha256, String physicalStructureSha256,
      String contentSha256, List<StoredRule> rules) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public HistoricalRevision revisionDetail(Long physicalId, Long revisionId) {
    requireDwd(physicalId);
    ModelingMappingSetPO root = current(physicalId, false);
    if (root == null || revisionId == null || revisionId <= 0) {
      throw new IllegalArgumentException("指定的映射冻结版本不存在");
    }
    ModelingMappingRevisionPO revision = findRevision(root, revisionId);
    if (revision == null) throw new IllegalArgumentException("映射冻结版本不存在或项目不匹配");
    String expected = VersionDigests.sha256Hex(
        revision.getLogicalSnapshotSha() + ":" + revision.getPhysicalStructureSha()
            + ":" + revision.getFrozenJson());
    if (!expected.equals(revision.getContentSha())) {
      throw new IllegalStateException("映射冻结历史快照校验失败");
    }
    return new HistoricalRevision(revision.getId(), root.getId(), revision.getRevisionNo(),
        root.getLogicalModelId(), root.getLogicalVersionNo(),
        revision.getLogicalSnapshotSha(), revision.getPhysicalStructureSha(),
        revision.getContentSha(), readRules(revision.getFrozenJson()));
  }

  /**
   * Called only after the publisher locks the physical root, in the SAME transaction.
   * Reusing the freeze validator refuses stale draft, semantic standard drift,
   * column renames, and an older revision chosen instead of the current frozen one.
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public PublicationEvidence requireFrozenForPublication(Long physicalId, Long revisionId) {
    requireDwd(physicalId);
    ModelingMappingSetPO root = current(physicalId, true);
    if (root == null || revisionId == null || revisionId <= 0) {
      throw new IllegalStateException("先保存并冻结当前 DWD 映射草稿");
    }
    ModelingMappingRevisionPO selected = findRevision(root, revisionId);
    if (selected == null || !Objects.equals(selected.getDraftRevision(), root.getDraftRevision())) {
      throw new IllegalStateException("所选冻结修订不属于当前映射草稿，请重新冻结");
    }
    Frozen rechecked = freeze(physicalId, root.getDraftRevision(), "publication-revalidation");
    if (!Objects.equals(rechecked.mappingRevisionId(), selected.getId())
        || !Objects.equals(rechecked.contentSha256(), selected.getContentSha())) {
      throw new IllegalStateException("冻结映射已失效或存在更新的修订，请重新审阅");
    }
    return new PublicationEvidence(selected.getId(), root.getId(),
        root.getLogicalModelId(), root.getLogicalVersionNo(),
        selected.getLogicalSnapshotSha(), selected.getPhysicalStructureSha(),
        selected.getContentSha());
  }

  private ModelingMappingRevisionPO findRevision(ModelingMappingSetPO root, Long revisionId) {
    return revisions.selectOne(new LambdaQueryWrapper<ModelingMappingRevisionPO>()
        .eq(ModelingMappingRevisionPO::getProjectId, currentProject.requireProjectId())
        .eq(ModelingMappingRevisionPO::getMappingSetId, root.getId())
        .eq(ModelingMappingRevisionPO::getId, revisionId));
  }

  private Frozen frozen(ModelingMappingRevisionPO po) {
    return new Frozen(po.getId(), po.getMappingSetId(), po.getRevisionNo(),
        po.getContentSha(), po.getDraftRevision(), "FROZEN_DESIGN_ONLY");
  }

  private Draft view(ModelingMappingSetPO po) {
    return new Draft(po.getId(), po.getPhysicalModelId(), po.getLogicalModelId(),
        po.getLogicalVersionNo(), po.getLogicalSnapshotSha(), po.getPhysicalStructureSha(),
        po.getDraftRevision(), readRules(po.getMappingJson()), "DRAFT_SAVED_NOT_PUBLISHED");
  }

  private String writeJson(List<StoredRule> rules) {
    try {
      return json.writeValueAsString(rules);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("映射草稿无法序列化", ex);
    }
  }

  private List<StoredRule> readRules(String content) {
    try {
      return List.copyOf(json.readValue(content, new TypeReference<List<StoredRule>>() {}));
    } catch (Exception ex) {
      throw new IllegalStateException("历史映射草稿损坏，不得据此冻结或发布", ex);
    }
  }
}
