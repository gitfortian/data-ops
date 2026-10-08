package io.yak.ops.business.modeling.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.dao.model.ModelingModelPO;
import io.yak.ops.business.semantic.api.SemanticStructureReferenceReader;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Modeling owns domainId/processId on physical models. Semantic can only ask
 * the read-only counts through this reverse SPI.
 */
@Component
@RequiredArgsConstructor
public class ModelingSemanticStructureReferenceReader implements SemanticStructureReferenceReader {
  private final ModelingModelMapper modelMapper;
  private final CurrentProject currentProject;

  @Override
  public long countProcessReferences(Long processId) {
    return modelMapper.selectCount(
        new LambdaQueryWrapper<ModelingModelPO>()
            .eq(ModelingModelPO::getProjectId, currentProject.requireProjectId())
            .eq(ModelingModelPO::getProcessId, processId));
  }

  @Override
  public long countDomainReferences(Long domainId) {
    return modelMapper.selectCount(
        new LambdaQueryWrapper<ModelingModelPO>()
            .eq(ModelingModelPO::getProjectId, currentProject.requireProjectId())
            .eq(ModelingModelPO::getDomainId, domainId));
  }
}
