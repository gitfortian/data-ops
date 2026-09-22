package io.yak.ops.business.lifecycle.binding;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.lifecycle.dao.mapper.LifecycleDispatchRecordMapper;
import io.yak.ops.business.lifecycle.dao.mapper.LifecycleModelBindingMapper;
import io.yak.ops.business.lifecycle.exception.LifecycleException;
import io.yak.ops.business.lifecycle.generate.TtlStatement;
import io.yak.ops.business.lifecycle.generate.TtlStatementGenerator;
import io.yak.ops.business.lifecycle.generate.TtlStatementGenerator.TtlTarget;
import io.yak.ops.business.lifecycle.policy.TtlPolicyService;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.modeling.api.ModelTtlQueryApi;
import io.yak.ops.business.modeling.api.ModelTtlQueryApi.TtlModelSource;
import io.yak.ops.business.semantic.api.LayerConfigApi;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.common.bean.po.lifecycle.LifecycleDispatchRecordPO;
import io.yak.ops.common.bean.po.lifecycle.LifecycleModelBindingPO;
import io.yak.ops.common.bean.po.lifecycle.LifecyclePolicyPO;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.BindingSource;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.Granularity;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.ModelState;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.PolicyScope;
import io.yak.ops.common.enums.lifecycle.LifecycleErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 模型 TTL 绑定 + 继承解析(ticket 82,design 3.1)。
 * 优先级:OVERRIDE(绑定) > LAYER_DEFAULT(层默认策略) > LEGACY_LAYER(旧 lifecycle_days 合成) > NONE。
 */
@Service
@RequiredArgsConstructor
public class ModelTtlBindingService {

  private final io.yak.ops.core.project.CurrentProject currentProject;

  private final LifecycleModelBindingMapper bindingMapper;
  private final LifecycleDispatchRecordMapper dispatchRecordMapper;
  private final TtlPolicyService policyService;
  private final ModelTtlQueryApi modelTtlQueryApi;
  private final LayerConfigApi layerConfigApi;
  private final BusinessAuditService auditService;

  public ModelTtlResolution resolve(Long modelId) {
    TtlModelSource model = modelTtlQueryApi.resolve(modelId);
    return build(model, findBinding(modelId), layer(model.layerCode()),
        lastDispatch(modelId), policyService.layerDefaultMap());
  }

  /** 监控/批量预览用:一次装载 binding/策略/下发记录,避免逐模型查询。 */
  public List<ModelTtlResolution> resolveAll() {
    Long projectId = currentProject.requireProjectId();
    Map<Long, LifecycleModelBindingPO> bindings = new HashMap<>();
    bindingMapper.selectList(new LambdaQueryWrapper<LifecycleModelBindingPO>()
            .eq(LifecycleModelBindingPO::getProjectId, projectId)
            .eq(LifecycleModelBindingPO::getDeleted, false))
        .forEach(b -> bindings.put(b.getModelId(), b));
    Map<Long, LifecycleDispatchRecordPO> lasts = latestDispatchByModel(projectId);
    Map<String, LifecyclePolicyPO> layerDefaults = policyService.layerDefaultMap();
    Map<String, WarehouseLayer> layers = new HashMap<>();
    List<WarehouseLayer> all = layerConfigApi.listLayers();
    if (all != null) {
      all.forEach(l -> layers.put(l.code().toUpperCase(), l));
    }
    return modelTtlQueryApi.listAll().stream()
        .map(m -> build(m, bindings.get(m.id()),
            m.layerCode() == null ? null : layers.get(m.layerCode().toUpperCase()),
            lasts.get(m.id()), layerDefaults))
        .toList();
  }

  /** 绑定/修改覆盖策略(policyId 可指向自定义或层默认策略)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ModelTtlResolution bind(Long modelId, Long policyId, String operator) {
    TtlModelSource model = modelTtlQueryApi.resolve(modelId);
    LifecyclePolicyPO policy = policyService.getByIdRaw(policyId);
    if (policy == null) {
      throw new LifecycleException(LifecycleErrorCode.POLICY_NOT_FOUND, "policyId=" + policyId);
    }
    Long projectId = currentProject.requireProjectId();
    LifecycleModelBindingPO binding = findBinding(modelId);
    if (binding == null) {
      binding = new LifecycleModelBindingPO();
      binding.setProjectId(projectId);
      binding.setModelId(modelId);
      binding.setPolicyId(policyId);
      binding.setCreatedBy(operator);
      binding.setUpdatedBy(operator);
      binding.setDeleted(false);
      bindingMapper.insert(binding);
    } else {
      binding.setPolicyId(policyId);
      binding.setUpdatedBy(operator);
      binding.setUpdateTime(LocalDateTime.now());
      bindingMapper.updateById(binding);
    }
    audit("TTL_BINDING_SET", binding, model, policy, operator, AuditEventType.RESOURCE_UPDATED,
        "模型 " + model.code() + " 绑定 TTL 策略 " + policy.getPolicyCode());
    return resolve(modelId);
  }

  /** 解绑:回到分层默认继承。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ModelTtlResolution unbind(Long modelId, String operator) {
    LifecycleModelBindingPO binding = findBinding(modelId);
    if (binding == null) {
      throw new LifecycleException(LifecycleErrorCode.BINDING_NOT_FOUND,
          "该模型当前继承分层默认策略,无需解绑");
    }
    binding.setDeleted(true);
    binding.setUpdateTime(LocalDateTime.now());
    bindingMapper.updateById(binding);
    TtlModelSource model = modelTtlQueryApi.resolve(modelId);
    audit("TTL_BINDING_UNSET", binding, model, null, operator, AuditEventType.RESOURCE_DELETED,
        "模型 " + model.code() + " 解除 TTL 覆盖绑定,回继承分层默认");
    return resolve(modelId);
  }

  public LifecycleModelBindingPO findBinding(Long modelId) {
    return bindingMapper.selectOne(new LambdaQueryWrapper<LifecycleModelBindingPO>()
        .eq(LifecycleModelBindingPO::getProjectId, currentProject.requireProjectId())
        .eq(LifecycleModelBindingPO::getModelId, modelId)
        .eq(LifecycleModelBindingPO::getDeleted, false)
        .last("LIMIT 1"));
  }

  private LifecycleDispatchRecordPO lastDispatch(Long modelId) {
    return dispatchRecordMapper.selectOne(new LambdaQueryWrapper<LifecycleDispatchRecordPO>()
        .eq(LifecycleDispatchRecordPO::getProjectId, currentProject.requireProjectId())
        .eq(LifecycleDispatchRecordPO::getModelId, modelId)
        .orderByDesc(LifecycleDispatchRecordPO::getId)
        .last("LIMIT 1"));
  }

  private Map<Long, LifecycleDispatchRecordPO> latestDispatchByModel(Long projectId) {
    Map<Long, LifecycleDispatchRecordPO> map = new HashMap<>();
    dispatchRecordMapper.selectList(new LambdaQueryWrapper<LifecycleDispatchRecordPO>()
            .eq(LifecycleDispatchRecordPO::getProjectId, projectId)
            .orderByAsc(LifecycleDispatchRecordPO::getId))
        .forEach(r -> map.put(r.getModelId(), r));
    return map;
  }

  private ModelTtlResolution build(TtlModelSource model, LifecycleModelBindingPO binding,
      WarehouseLayer layer, LifecycleDispatchRecordPO last,
      Map<String, LifecyclePolicyPO> layerDefaults) {
    LifecyclePolicyPO policy = null;
    BindingSource source = BindingSource.NONE;
    boolean virtual = false;
    if (binding != null) {
      policy = policyService.getByIdRaw(binding.getPolicyId());
      source = BindingSource.OVERRIDE;
    }
    if (policy == null && layer != null) {
      policy = layerDefaults.get(layer.code().toUpperCase());
      if (policy != null) {
        source = BindingSource.LAYER_DEFAULT;
      }
    }
    if (policy == null && layer != null && layer.lifecycleDays() != null
        && layer.lifecycleDays() > 0) {
      policy = legacyVirtualPolicy(layer);
      source = BindingSource.LEGACY_LAYER;
      virtual = true;
    }
    if (policy == null) {
      return ModelTtlResolution.unset(model, layer);
    }
    boolean hasTimePartition = StringUtils.hasText(model.partitionType());
    TtlStatement statement = TtlStatementGenerator.generate(policy,
        new TtlTarget(model.dialect(), layer == null ? null : layer.databaseName(),
            model.tableName()));
    ModelState state = deriveState(policy, last, virtual);
    String reason = null;
    if (!hasTimePartition) {
      reason = LifecycleErrorCode.NO_TIME_PARTITION.getMessage() + ":模型未配置分区,下发无意义";
    } else if (layer == null || layer.datasourceId() == null
        || !StringUtils.hasText(layer.databaseName())) {
      reason = LifecycleErrorCode.LAYER_CONFIG_MISSING.getMessage();
    } else if (!statement.writable()) {
      reason = statement.note();
    }
    return new ModelTtlResolution(model, layer, source, policy, virtual, state, last,
        statement, hasTimePartition, reason == null, reason);
  }

  /** D5 状态机:无记录=DRIFT;最近失败=FAILED;策略更新晚于下发快照=DRIFT;否则 APPLIED。 */
  static ModelState deriveState(LifecyclePolicyPO policy, LifecycleDispatchRecordPO last,
      boolean virtualPolicy) {
    if (last == null) {
      return ModelState.DRIFT;
    }
    String status = last.getStatus();
    if ("FAILED".equals(status) || "RETRYING".equals(status) || "EXHAUSTED".equals(status)) {
      return ModelState.FAILED;
    }
    if (!virtualPolicy && !Objects.equals(last.getPolicyId(), policy.getId())) {
      return ModelState.DRIFT;
    }
    if (policy.getUpdateTime() != null && last.getPolicyUpdatedAt() != null
        && policy.getUpdateTime().isAfter(last.getPolicyUpdatedAt())) {
      return ModelState.DRIFT;
    }
    return ModelState.APPLIED;
  }

  private LifecyclePolicyPO legacyVirtualPolicy(WarehouseLayer layer) {
    LifecyclePolicyPO po = new LifecyclePolicyPO();
    po.setPolicyCode("legacy_" + layer.code().toLowerCase());
    po.setPolicyName(layer.name() + "分层旧配置(合成)");
    po.setScopeType(PolicyScope.LAYER_DEFAULT.name());
    po.setLayerCode(layer.code().toUpperCase());
    po.setPartitionGranularity(Granularity.DAY.name());
    po.setDestroyDays(layer.lifecycleDays());
    po.setBuiltin(false);
    po.setStatus("ENABLED");
    return po;
  }

  private WarehouseLayer layer(String layerCode) {
    return layerCode == null ? null : layerConfigApi.resolveByCode(layerCode);
  }

  private void audit(String opType, LifecycleModelBindingPO binding, TtlModelSource model,
      LifecyclePolicyPO policy, String operator, AuditEventType event, String message) {
    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        opType, "Model TTL binding", "TTL_BINDING",
        String.valueOf(binding.getModelId()), model == null ? null : model.code(),
        "APPLICATION", policy == null ? Map.of() : Map.of("policyId", policy.getId())));
    AuditTransactions.completeOnCommit(audit, event, message,
        Map.of("modelId", binding.getModelId()), null);
  }
}
