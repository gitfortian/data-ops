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
}
