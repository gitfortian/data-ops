package io.yak.ops.business.semantic.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.dao.mapper.SemanticPresetTemplateMapper;
import io.yak.ops.business.semantic.preset.PresetTemplate;
import io.yak.ops.common.bean.po.semantic.SemanticPresetTemplatePO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for the platform preset template table. */
@Repository
@RequiredArgsConstructor
public class PresetTemplateRepositoryAdapter implements SemanticPresetTemplateRepository {

  private final SemanticPresetTemplateMapper mapper;

  @Override
  public List<PresetTemplate> findAll() {
    return mapper
        .selectList(
            new LambdaQueryWrapper<SemanticPresetTemplatePO>()
                .orderByAsc(SemanticPresetTemplatePO::getKind)
                .orderByAsc(SemanticPresetTemplatePO::getSortOrder)
                .orderByAsc(SemanticPresetTemplatePO::getId))
        .stream()
        .map(PresetTemplateRepositoryAdapter::toDomain)
        .toList();
  }

  private static PresetTemplate toDomain(SemanticPresetTemplatePO po) {
    return new PresetTemplate(
        po.getId(),
        StandardKind.fromStored(po.getKind()).orElseThrow(() -> new IllegalArgumentException(po.getKind())),
        po.getStdCode(),
        po.getStdName(),
        po.getDescription(),
        po.getSortOrder(),
        new Standard.KindFields(
            po.getScope(),
            po.getLayer(),
            po.getRuleExpr(),
            po.getExample(),
            po.getTypeCode(),
            po.getStdType(),
            po.getSourceMapping(),
            po.getCodeSetCode(),
            po.getCodeValue(),
            po.getCodeLabel(),
            po.getUnitCode(),
            po.getUnitType(),
            po.getCaliberCode(),
            po.getCalRule(),
            po.getBusinessDesc(),
            po.getLevelCode(),
            po.getMaskRule()));
  }
}
