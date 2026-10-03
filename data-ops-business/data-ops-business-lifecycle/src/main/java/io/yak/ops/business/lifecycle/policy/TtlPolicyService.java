package io.yak.ops.business.lifecycle.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.lifecycle.exception.LifecycleException;
import io.yak.ops.business.lifecycle.schedule.LifecycleScheduleEngineBridge;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.lifecycle.dao.model.LifecycleModelBindingPO;
import io.yak.ops.business.lifecycle.dao.model.LifecyclePolicyPO;
import io.yak.ops.common.enums.PublishState;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.Granularity;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.PolicyScope;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.Status;
import io.yak.ops.common.enums.lifecycle.LifecycleErrorCode;
import io.yak.ops.common.util.AuditDiffs;
import io.yak.ops.business.lifecycle.dao.mapper.LifecycleModelBindingMapper;
import io.yak.ops.business.lifecycle.dao.mapper.LifecyclePolicyMapper;
import io.yak.ops.business.semantic.api.LayerConfigApi;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** TTL 策略 CRUD + 分层默认初始化(ticket 81)。策略是生命周期唯一事实源(D1)。 */
@Service
@RequiredArgsConstructor
public class TtlPolicyService {

  private final io.yak.ops.core.project.CurrentProject currentProject;

  /** 新增/更新命令;policyCode 空则自动生成。 */
  public record UpsertCommand(
      String policyCode,
      String policyName,
      String scopeType,
      String layerCode,
      String partitionGranularity,
      Integer hotDays,
      Integer coldDays,
      Integer destroyDays,
      String remark) {}

  /** 列表视图:含模型引用数(删除保护展示)+发布态/未发布修改标记。 */
  public record PolicyView(
      Long id,
      String policyCode,
      String policyName,
      String scopeType,
      String layerCode,
      String partitionGranularity,
      Integer hotDays,
      Integer coldDays,
      Integer destroyDays,
      boolean builtin,
      String status,
      String publishState,
      Integer latestVersionNo,
      Integer draftRevision,
      boolean hasPendingDraft,
      String remark,
      long referenceCount,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}

  /** 分层模板预填值(GET /policies/layer-template)。 */
  public record LayerTemplateView(
      String layerCode,
      String layerName,
      Integer hotDays,
      Integer coldDays,
      Integer destroyDays,
      String partitionGranularity) {}

  private final LifecyclePolicyMapper policyMapper;
  private final LifecycleModelBindingMapper bindingMapper;
  private final LayerConfigApi layerConfigApi;
  private final BusinessAuditService auditService;
  private final LifecycleScheduleEngineBridge scheduleBridge;
  private final TtlPolicyVersionService versionService;

  public PageData<PolicyView> page(int pageNo, int pageSize, String scopeType,
      String layerCode, String keyword) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<LifecyclePolicyPO> wrapper = new LambdaQueryWrapper<LifecyclePolicyPO>()
        .eq(LifecyclePolicyPO::getProjectId, projectId)
        .eq(LifecyclePolicyPO::getDeleted, false)
        .eq(StringUtils.hasText(scopeType), LifecyclePolicyPO::getScopeType, scopeType)
        .eq(StringUtils.hasText(layerCode), LifecyclePolicyPO::getLayerCode, layerCode)
        .and(StringUtils.hasText(keyword), w -> w
            .like(LifecyclePolicyPO::getPolicyName, keyword)
            .or().like(LifecyclePolicyPO::getPolicyCode, keyword))
        .orderByDesc(LifecyclePolicyPO::getBuiltin)
        .orderByAsc(LifecyclePolicyPO::getId);
    Page<LifecyclePolicyPO> result =
        policyMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    List<PolicyView> views = result.getRecords().stream().map(po -> {
      long refs = countReferences(projectId, po.getId());
      return toView(po, refs);
    }).toList();
    return new PageData<>(views, result.getTotal(), result.getPages(),
        (int) result.getCurrent(), (int) result.getSize());
  }

  public PolicyView get(Long id) {
    LifecyclePolicyPO po = requirePolicy(id);
    return toView(po, countReferences(po.getProjectId(), id));
  }

  /** 层默认策略解析(binding/monitor 读路径复用);无则 null。 */
  public LifecyclePolicyPO findLayerDefault(String layerCode) {
    if (!StringUtils.hasText(layerCode)) {
      return null;
    }
    return policyMapper.selectOne(new LambdaQueryWrapper<LifecyclePolicyPO>()
        .eq(LifecyclePolicyPO::getProjectId, currentProject.requireProjectId())
        .eq(LifecyclePolicyPO::getScopeType, PolicyScope.LAYER_DEFAULT.name())
        .eq(LifecyclePolicyPO::getLayerCode, layerCode.toUpperCase())
        .eq(LifecyclePolicyPO::getDeleted, false)
        .last("LIMIT 1"));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public PolicyView create(UpsertCommand cmd, String operator) {
    Long projectId = currentProject.requireProjectId();
    PolicyScope scope = parseScope(cmd.scopeType());
    validateRetention(cmd);
    WarehouseLayer layer = requireLayerIfNeeded(scope, cmd.layerCode());

    String code = StringUtils.hasText(cmd.policyCode())
        ? cmd.policyCode().trim() : generateCode(scope, cmd, projectId);
    if (findByCode(projectId, code) != null) {
      throw new LifecycleException(LifecycleErrorCode.DUPLICATE_POLICY_CODE, code);
    }
    if (scope == PolicyScope.LAYER_DEFAULT && findLayerDefault(cmd.layerCode()) != null) {
      throw new LifecycleException(LifecycleErrorCode.LAYER_DEFAULT_EXISTS, cmd.layerCode());
    }

    LifecyclePolicyPO po = new LifecyclePolicyPO();
    po.setProjectId(projectId);
    po.setPolicyCode(code);
    po.setPolicyName(StringUtils.hasText(cmd.policyName())
        ? cmd.policyName().trim() : defaultName(scope, layer, cmd));
    po.setScopeType(scope.name());
    po.setLayerCode(scope == PolicyScope.LAYER_DEFAULT ? layer.code().toUpperCase() : null);
    po.setPartitionGranularity(parseGranularity(cmd.partitionGranularity()).name());
    applyRetention(po, cmd);
    po.setBuiltin(scope == PolicyScope.LAYER_DEFAULT);
    po.setStatus(Status.ENABLED.name());
    po.setPublishState(PublishState.DRAFT.name());
    po.setDraftRevision(0);
    po.setLatestVersionNo(0);
    po.setRemark(cmd.remark());
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setDeleted(false);
    insertOrThrowDuplicate(po);

    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "TTL_POLICY_CREATE", "Create TTL policy", "TTL_POLICY",
        String.valueOf(po.getId()), po.getPolicyCode(), "APPLICATION",
        Map.of("scopeType", scope.name())));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_CREATED,
        "创建 TTL 策略 " + po.getPolicyCode(), Map.of("id", po.getId()), null);
    return toView(po, 0);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public PolicyView update(Long id, UpsertCommand cmd, String operator) {
    LifecyclePolicyPO existing = requirePolicy(id);
    Map<String, Object> beforeSnapshot = TtlPolicyVersionService.draftSnapshot(existing);
    PolicyScope scope = PolicyScope.valueOf(existing.getScopeType());
    validateRetention(cmd);
    if (scope == PolicyScope.LAYER_DEFAULT) {
      requireLayerIfNeeded(scope, existing.getLayerCode());
    }
    if (StringUtils.hasText(cmd.policyCode())
        && !cmd.policyCode().trim().equals(existing.getPolicyCode())) {
      if (findByCode(existing.getProjectId(), cmd.policyCode().trim()) != null) {
        throw new LifecycleException(LifecycleErrorCode.DUPLICATE_POLICY_CODE, cmd.policyCode());
      }
      existing.setPolicyCode(cmd.policyCode().trim());
    }
    if (StringUtils.hasText(cmd.policyName())) {
      existing.setPolicyName(cmd.policyName().trim());
    }
    if (scope == PolicyScope.CUSTOM && StringUtils.hasText(cmd.partitionGranularity())) {
      existing.setPartitionGranularity(parseGranularity(cmd.partitionGranularity()).name());
    }
    applyRetention(existing, cmd);
    existing.setRemark(cmd.remark());
    existing.setDraftRevision(
        Objects.requireNonNullElse(existing.getDraftRevision(), 0) + 1);
    existing.setUpdatedBy(operator);
    existing.setUpdateTime(LocalDateTime.now());
    policyMapper.updateById(existing);

    // 编辑只动草稿(契约 C4);线上继续读最新发布快照。审计必须带 before/after(契约 C5)。
    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "TTL_POLICY_UPDATE", "Update TTL policy draft", "TTL_POLICY",
        String.valueOf(id), existing.getPolicyCode(), "APPLICATION", Map.of()));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
        "更新 TTL 策略草稿 " + existing.getPolicyCode(),
        Map.of("id", id,
            "draftRevision", existing.getDraftRevision(),
            "diff", AuditDiffs.diff(beforeSnapshot,
                TtlPolicyVersionService.draftSnapshot(existing))),
        null);
    return toView(existing, countReferences(existing.getProjectId(), id));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id, String operator) {
    LifecyclePolicyPO existing = requirePolicy(id);
    if (Boolean.TRUE.equals(existing.getBuiltin())) {
      throw new LifecycleException(LifecycleErrorCode.POLICY_NOT_FOUND,
          "内置分层默认策略不可删除,可编辑或直接禁用");
    }
    long refs = countReferences(existing.getProjectId(), id);
    if (refs > 0) {
      throw new LifecycleException(LifecycleErrorCode.POLICY_REFERENCED,
          "被 " + refs + " 个模型绑定引用");
    }
    existing.setDeleted(true);
    existing.setUpdatedBy(operator);
    existing.setUpdateTime(LocalDateTime.now());
    policyMapper.updateById(existing);

    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "TTL_POLICY_DELETE", "Delete TTL policy", "TTL_POLICY",
        String.valueOf(id), existing.getPolicyCode(), "APPLICATION", Map.of()));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_DELETED,
        "删除 TTL 策略 " + existing.getPolicyCode(), Map.of("id", id), null);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public PolicyView changeStatus(Long id, String status, String operator) {
    LifecyclePolicyPO existing = requirePolicy(id);
    Status target;
    try {
      target = Status.valueOf(status);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new LifecycleException(LifecycleErrorCode.INVALID_RETENTION, "状态仅支持 ENABLED/DISABLED");
    }
    existing.setStatus(target.name());
    existing.setUpdatedBy(operator);
    existing.setUpdateTime(LocalDateTime.now());
    policyMapper.updateById(existing);

    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "TTL_POLICY_STATUS", "Change TTL policy status", "TTL_POLICY",
        String.valueOf(id), existing.getPolicyCode(), "APPLICATION",
        Map.of("status", target.name())));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
        "策略 " + existing.getPolicyCode() + " 状态 -> " + target.name(),
        Map.of("id", id, "status", target.name()), null);
    return toView(existing, countReferences(existing.getProjectId(), id));
  }

  /** 幂等初始化分层默认策略:仅补齐缺失层,已存在的不覆盖。返回新建条数。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int initializeLayerDefaults(String operator) {
    Long projectId = currentProject.requireProjectId();
    int created = 0;
    for (WarehouseLayer layer : safeLayers()) {
      if (findLayerDefault(layer.code()) != null) {
        continue;
      }
      LayerTtlTemplate.TemplateValues tv =
          LayerTtlTemplate.forLayer(layer.code(), layer.lifecycleDays());
      LifecyclePolicyPO po = new LifecyclePolicyPO();
      po.setProjectId(projectId);
      po.setPolicyCode("ttl_" + layer.code().toLowerCase() + "_default");
      po.setPolicyName(layer.name() + "默认策略");
      po.setScopeType(PolicyScope.LAYER_DEFAULT.name());
      po.setLayerCode(layer.code().toUpperCase());
      po.setPartitionGranularity(LayerTtlTemplate.defaultGranularity().name());
      po.setHotDays(tv.hotDays());
      po.setColdDays(tv.coldDays());
      po.setDestroyDays(tv.destroyDays());
      po.setBuiltin(true);
      po.setStatus(Status.ENABLED.name());
      po.setPublishState(PublishState.DRAFT.name());
      po.setDraftRevision(0);
      po.setLatestVersionNo(0);
      po.setCreatedBy(operator);
      po.setUpdatedBy(operator);
      po.setDeleted(false);
      insertOrThrowDuplicate(po);
      created++;
    }
    if (created > 0) {
      AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
          "TTL_POLICY_INIT", "Initialize layer default policies", "TTL_POLICY",
          null, "layer-defaults", "APPLICATION", Map.of("created", created)));
      AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_CREATED,
          "初始化分层默认策略 " + created + " 条", Map.of("created", created), null);
    }
    scheduleBridge.ensureProjectAlarms(projectId);
    return created;
  }

  /** 前端预填模板:按当前分层配置 + 旧 lifecycle_days 兜底合成(D1)。 */
  public List<LayerTemplateView> layerTemplate() {
    List<LayerTemplateView> views = new ArrayList<>();
    for (WarehouseLayer layer : safeLayers()) {
      LayerTtlTemplate.TemplateValues tv =
          LayerTtlTemplate.forLayer(layer.code(), layer.lifecycleDays());
      views.add(new LayerTemplateView(layer.code().toUpperCase(), layer.name(),
          tv.hotDays(), tv.coldDays(), tv.destroyDays(),
          LayerTtlTemplate.defaultGranularity().name()));
    }
    if (views.isEmpty()) {
      LayerTtlTemplate.presets().forEach((code, tv) -> views.add(new LayerTemplateView(
          code, code, tv.hotDays(), tv.coldDays(), tv.destroyDays(),
          LayerTtlTemplate.defaultGranularity().name())));
    }
    return views;
  }

  // ---------- internal ----------

  private LifecyclePolicyPO requirePolicy(Long id) {
    LifecyclePolicyPO po = policyMapper.selectOne(new LambdaQueryWrapper<LifecyclePolicyPO>()
        .eq(LifecyclePolicyPO::getProjectId, currentProject.requireProjectId())
        .eq(LifecyclePolicyPO::getId, id)
        .eq(LifecyclePolicyPO::getDeleted, false));
    if (po == null) {
      throw new LifecycleException(LifecycleErrorCode.POLICY_NOT_FOUND, "id=" + id);
    }
    return po;
  }

  private LifecyclePolicyPO findByCode(Long projectId, String code) {
    return policyMapper.selectOne(new LambdaQueryWrapper<LifecyclePolicyPO>()
        .eq(LifecyclePolicyPO::getProjectId, projectId)
        .eq(LifecyclePolicyPO::getPolicyCode, code)
        .eq(LifecyclePolicyPO::getDeleted, false)
        .last("LIMIT 1"));
  }

  private long countReferences(Long projectId, Long policyId) {
    Long count = bindingMapper.selectCount(new LambdaQueryWrapper<LifecycleModelBindingPO>()
        .eq(LifecycleModelBindingPO::getProjectId, projectId)
        .eq(LifecycleModelBindingPO::getPolicyId, policyId)
        .eq(LifecycleModelBindingPO::getDeleted, false));
    return count == null ? 0 : count;
  }

  private void insertOrThrowDuplicate(LifecyclePolicyPO po) {
    try {
      policyMapper.insert(po);
    } catch (DuplicateKeyException e) {
      throw new LifecycleException(LifecycleErrorCode.DUPLICATE_POLICY_CODE, po.getPolicyCode());
    }
  }

  private WarehouseLayer requireLayerIfNeeded(PolicyScope scope, String layerCode) {
    if (scope != PolicyScope.LAYER_DEFAULT) {
      return null;
    }
    if (!StringUtils.hasText(layerCode)) {
      throw new LifecycleException(LifecycleErrorCode.INVALID_RETENTION, "分层默认策略必须指定 layerCode");
    }
    WarehouseLayer layer = layerConfigApi.resolveByCode(layerCode);
    if (layer == null) {
      throw new LifecycleException(LifecycleErrorCode.INVALID_RETENTION,
          "分层 " + layerCode + " 不存在,请先在分层配置中创建");
    }
    return layer;
  }

  private List<WarehouseLayer> safeLayers() {
    List<WarehouseLayer> layers = layerConfigApi.listLayers();
    return layers == null ? List.of() : layers;
  }

  private PolicyScope parseScope(String scopeType) {
    if (!StringUtils.hasText(scopeType)) {
      return PolicyScope.CUSTOM;
    }
    try {
      return PolicyScope.valueOf(scopeType.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new LifecycleException(LifecycleErrorCode.INVALID_RETENTION, "适用范围仅支持 LAYER_DEFAULT/CUSTOM");
    }
  }

  private Granularity parseGranularity(String granularity) {
    if (!StringUtils.hasText(granularity)) {
      return LayerTtlTemplate.defaultGranularity();
    }
    try {
      return Granularity.valueOf(granularity.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new LifecycleException(LifecycleErrorCode.INVALID_RETENTION, "分区粒度仅支持 DAY/MONTH/YEAR");
    }
  }

  /** 47005:热≤冷≤销毁(null 视为无界);给定段必须为正。 */
  static void validateRetention(UpsertCommand cmd) {
    Integer hot = cmd.hotDays();
    Integer cold = cmd.coldDays();
    Integer destroy = cmd.destroyDays();
    for (Integer v : new Integer[] {hot, cold, destroy}) {
      if (v != null && v <= 0) {
        throw new LifecycleException(LifecycleErrorCode.INVALID_RETENTION, "保留天数必须为正整数");
      }
    }
    if (hot != null && cold != null && hot > cold) {
      throw new LifecycleException(LifecycleErrorCode.INVALID_RETENTION,
          "热数据(" + hot + "天)不能晚于冷数据(" + cold + "天)");
    }
    if (cold != null && destroy != null && cold > destroy) {
      throw new LifecycleException(LifecycleErrorCode.INVALID_RETENTION,
          "冷数据(" + cold + "天)不能晚于销毁(" + destroy + "天)");
    }
    if (hot != null && destroy != null && hot > destroy) {
      throw new LifecycleException(LifecycleErrorCode.INVALID_RETENTION,
          "热数据(" + hot + "天)不能晚于销毁(" + destroy + "天)");
    }
  }

  private void applyRetention(LifecyclePolicyPO po, UpsertCommand cmd) {
    po.setHotDays(cmd.hotDays());
    po.setColdDays(cmd.coldDays());
    po.setDestroyDays(cmd.destroyDays());
  }

  private String generateCode(PolicyScope scope, UpsertCommand cmd, Long projectId) {
    String base = scope == PolicyScope.LAYER_DEFAULT && StringUtils.hasText(cmd.layerCode())
        ? "ttl_" + cmd.layerCode().trim().toLowerCase() + "_default"
        : "ttl_custom_" + System.currentTimeMillis();
    String code = base;
    int suffix = 1;
    while (findByCode(projectId, code) != null) {
      code = base + "_" + suffix++;
    }
    return code;
  }

  private String defaultName(PolicyScope scope, WarehouseLayer layer, UpsertCommand cmd) {
    if (scope == PolicyScope.LAYER_DEFAULT && layer != null) {
      return layer.name() + "默认策略";
    }
    return "自定义策略";
  }

  private PolicyView toView(LifecyclePolicyPO po, long referenceCount) {
    return new PolicyView(po.getId(), po.getPolicyCode(), po.getPolicyName(),
        po.getScopeType(), po.getLayerCode(), po.getPartitionGranularity(),
        po.getHotDays(), po.getColdDays(), po.getDestroyDays(),
        Boolean.TRUE.equals(po.getBuiltin()), po.getStatus(),
        Objects.requireNonNullElse(po.getPublishState(), PublishState.DRAFT.name()),
        Objects.requireNonNullElse(po.getLatestVersionNo(), 0),
        Objects.requireNonNullElse(po.getDraftRevision(), 0),
        versionService.hasPendingDraft(po),
        po.getRemark(),
        referenceCount, po.getCreateTime(), po.getUpdateTime());
  }

  /** 层默认策略映射(layerCode -> policy),监控批量渲染用。仅返回**生效**（已发布且启用）内容。 */
  public Map<String, LifecyclePolicyPO> layerDefaultMap() {
    Long projectId = currentProject.requireProjectId();
    List<LifecyclePolicyPO> policies = policyMapper.selectList(
        new LambdaQueryWrapper<LifecyclePolicyPO>()
            .eq(LifecyclePolicyPO::getProjectId, projectId)
            .eq(LifecyclePolicyPO::getScopeType, PolicyScope.LAYER_DEFAULT.name())
            .eq(LifecyclePolicyPO::getDeleted, false));
    Map<String, LifecyclePolicyPO> map = new LinkedHashMap<>();
    policies.forEach(p -> {
      LifecyclePolicyPO effective = versionService.effective(p);
      if (effective != null) {
        map.put(Objects.requireNonNullElse(effective.getLayerCode(), "").toUpperCase(), effective);
      }
    });
    return map;
  }

  /** 消费方入口（绑定校验/下发）:返回发布快照覆盖后的生效内容;草稿/已下线返回 null。 */
  public LifecyclePolicyPO getByIdRaw(Long id) {
    LifecyclePolicyPO po = policyMapper.selectOne(new LambdaQueryWrapper<LifecyclePolicyPO>()
        .eq(LifecyclePolicyPO::getProjectId, currentProject.requireProjectId())
        .eq(LifecyclePolicyPO::getId, id)
        .eq(LifecyclePolicyPO::getDeleted, false));
    return po == null ? null : versionService.effective(po);
  }
}
