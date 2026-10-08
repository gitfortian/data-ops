package io.yak.ops.business.modeling.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelColumnMapper;
import io.yak.ops.business.modeling.dao.model.ModelingModelColumnPO;
import io.yak.ops.business.semantic.api.SemanticFieldReferenceReader;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Modeling owns stdFieldId on model columns; Semantic can only ask for the
 * read-only reference count, never query Modeling tables directly.
 */
@Component
@RequiredArgsConstructor
public class ModelingSemanticFieldReferenceReader implements SemanticFieldReferenceReader {
  private final ModelingModelColumnMapper columnMapper;
  private final CurrentProject currentProject;

  @Override
  public long countFieldReferences(Long fieldId) {
    // Count even recycle-bin model columns: their stored references may become
    // active on recovery. Project identity is taken from the trusted context.
    return columnMapper.selectCount(
        new LambdaQueryWrapper<ModelingModelColumnPO>()
            .eq(ModelingModelColumnPO::getProjectId, currentProject.requireProjectId())
            .eq(ModelingModelColumnPO::getStdFieldId, fieldId));
  }
}
