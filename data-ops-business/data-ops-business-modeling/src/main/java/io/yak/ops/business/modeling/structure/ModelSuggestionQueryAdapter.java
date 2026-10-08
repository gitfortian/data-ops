package io.yak.ops.business.modeling.structure;

import io.yak.ops.business.modeling.api.ModelSuggestionQueryApi;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ModelSuggestionQueryAdapter implements ModelSuggestionQueryApi {
  private final ModelStructureService structures;
  private final ActionAuthorization authorization;

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Context require(long modelId) {
    authorization.requirePermission(ModelingPermissionCode.READ);
    var view = structures.getForUpdate(modelId);
    return new Context(modelId, view.modelName(), view.dialect(), StructureFingerprint.of(view));
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Fields fields(long modelId) {
    authorization.requirePermission(ModelingPermissionCode.READ);
    var view = structures.getForUpdate(modelId);
    if (view.columns().isEmpty() || view.columns().size() > 100) throw new IllegalArgumentException("模型字段须为 1–100 项，请缩小任务范围");
    var fields = view.columns().stream().map(c -> new Field(c.columnName(), c.dataType(),
        c.businessDescription() == null ? "" : c.businessDescription())).toList();
    int size = 0;
    for (var field : fields) {
      if (field.name() == null || !field.name().matches("[A-Za-z_][A-Za-z0-9_]{0,127}")
          || field.type() == null || field.type().isBlank() || field.type().length() > 64 || field.description().length() > 512) {
        throw new IllegalArgumentException("模型字段超过指标草稿的支持范围");
      }
      size += field.name().length() + field.type().length() + field.description().length();
    }
    if (size > 16000) throw new IllegalArgumentException("模型字段上下文超过指标草稿范围");
    return new Fields(modelId, StructureFingerprint.of(view), fields);
  }
}
