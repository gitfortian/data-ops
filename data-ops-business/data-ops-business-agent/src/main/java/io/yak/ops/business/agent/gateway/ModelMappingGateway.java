package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.ModelMappingContext;
import io.yak.ops.business.agent.domain.ModelMappingProposal;
import io.yak.ops.business.agent.domain.ModelMappingSuggestion;
import io.yak.ops.business.agent.domain.ModelMappingTarget;
import io.yak.ops.business.modeling.api.MappingSuggestionQueryApi;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class ModelMappingGateway {
  private final ObjectProvider<MappingSuggestionQueryApi> models;

  public ModelMappingContext prepare(ModelMappingTarget target) {
    var api = models.getIfAvailable();
    if (api == null) throw new IllegalStateException("模型映射辅助读取未装配");
    var source = api.require(target.modelId(), target.columnName(), target.datasourceId(), target.database(), target.table(), target.keyword());
    return new ModelMappingContext(source.modelName(), source.dialect(), source.targetColumn(), source.targetType(),
        source.targetDescription(), source.definition(), source.sourceDefinition(), source.columns().stream()
        .map(c -> new ModelMappingContext.SourceColumn(c.name(), c.type(), c.description(), c.nullable())).toList(), source.truncated());
  }

  public ModelMappingSuggestion validate(ModelMappingTarget target, ModelMappingContext original,
      ModelMappingProposal proposal, int skillVersion, String skillHash) {
    var current = prepare(target);
    if (!current.definition().equals(original.definition()) || !current.sourceDefinition().equals(original.sourceDefinition())) {
      throw new IllegalArgumentException("目标字段、映射或源目录已变化，请重新生成");
    }
    if (proposal == null || proposal.candidates() == null || proposal.candidates().size() > 3
        || proposal.questions() == null || proposal.questions().size() > 3
        || proposal.questions().stream().anyMatch(q -> q == null || q.isBlank() || q.length() > 512)) {
      throw new IllegalArgumentException("模型映射输出不符合候选协议");
    }
    var seen = new HashSet<String>();
    var candidates = proposal.candidates().stream().map(c -> {
      if (c == null || c.sourceColumn() == null || !seen.add(c.sourceColumn())
          || c.reason() == null || c.reason().isBlank() || c.reason().length() > 512
          || original.columns().stream().noneMatch(s -> s.name().equals(c.sourceColumn()))) {
        throw new IllegalArgumentException("来源字段超出本轮范围或输出格式无效");
      }
      var field = current.columns().stream().filter(s -> s.name().equals(c.sourceColumn())).findFirst()
          .orElseThrow(() -> new IllegalArgumentException("来源字段已不在当前候选范围"));
      return new ModelMappingSuggestion.Candidate(field.name(), field.type(), field.nullable(), c.reason());
    }).toList();
    return new ModelMappingSuggestion("MODEL_MAPPING", target, original.definition(), original.sourceDefinition(),
        current.targetType(), skillVersion, skillHash, original.truncated(), candidates, proposal.questions());
  }

  public ModelMappingSuggestion revalidate(ModelMappingSuggestion value) {
    if (value == null || !"MODEL_MAPPING".equals(value.kind()) || value.target() == null
        || value.candidates() == null || value.candidates().isEmpty() || value.candidates().size() > 3
        || value.candidates().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("请选择有效映射候选");
    var source = prepare(value.target());
    if (!source.definition().equals(value.expectedDefinition()) || !source.sourceDefinition().equals(value.sourceDefinition())) {
      throw new IllegalArgumentException("映射或源目录已变化，请重新生成");
    }
    return validate(value.target(), source, new ModelMappingProposal(value.candidates().stream()
        .map(c -> new ModelMappingProposal.Choice(c.sourceColumn(), c.reason())).toList(), List.of()), value.skillVersion(), value.skillHash());
  }
}
