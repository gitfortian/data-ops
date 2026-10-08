package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.StandardMatchContext;
import io.yak.ops.business.agent.domain.StandardMatchProposal;
import io.yak.ops.business.agent.domain.StandardMatchSuggestion;
import io.yak.ops.business.agent.domain.StandardMatchTarget;
import io.yak.ops.business.modeling.api.ModelSuggestionQueryApi;
import io.yak.ops.business.semantic.api.StandardSuggestionQueryApi;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class StandardMatchGateway {
  private final ObjectProvider<ModelSuggestionQueryApi> models;
  private final ObjectProvider<StandardSuggestionQueryApi> standards;

  public StandardMatchContext prepare(StandardMatchTarget target) {
    var model = modelApi().require(target.modelId());
    var pool = standardApi().types(target.keyword());
    return new StandardMatchContext(model.name(), model.definition(), pool.candidates().stream()
        .map(c -> new StandardMatchContext.TypeCandidate(c.id(), c.version(), c.code(), c.name(), c.stdType(), c.description())).toList(), pool.truncated());
  }

  public StandardMatchSuggestion validate(StandardMatchTarget target, StandardMatchContext original,
      StandardMatchProposal proposal, int skillVersion, String skillHash) {
    if (!modelApi().require(target.modelId()).definition().equals(original.definition())) {
      throw new IllegalArgumentException("模型定义已变化，请重新生成标准匹配");
    }
    if (proposal == null || proposal.candidates() == null || proposal.candidates().size() > 3
        || proposal.questions() == null || proposal.questions().size() > 3
        || proposal.questions().stream().anyMatch(q -> q == null || q.isBlank() || q.length() > 512)
        || (proposal.fieldDescription() != null && (proposal.fieldDescription().isBlank() || proposal.fieldDescription().length() > 512))) {
      throw new IllegalArgumentException("标准匹配输出不符合候选协议");
    }
    // Recheck Semantic access even when the model produced no candidates.
    standardApi().types(target.keyword());
    var seen = new HashSet<Long>();
    var candidates = proposal.candidates().stream().map(c -> {
      if (c == null || !seen.add(c.standardId()) || c.reason() == null || c.reason().isBlank() || c.reason().length() > 512
          || original.candidates().stream().noneMatch(s -> s.id() == c.standardId() && s.version() == c.version())) {
        throw new IllegalArgumentException("候选超出本轮来源范围或格式无效");
      }
      var source = standardApi().requireType(c.standardId(), c.version());
      return new StandardMatchSuggestion.Candidate(source.id(), source.version(), source.code(), source.name(), source.stdType(), c.reason());
    }).toList();
    return new StandardMatchSuggestion("STANDARD_MATCH", target, original.definition(), skillVersion, skillHash,
        original.truncated(), candidates, proposal.questions(), proposal.fieldDescription());
  }

  public StandardMatchSuggestion revalidate(StandardMatchSuggestion value) {
    if (value == null || !"STANDARD_MATCH".equals(value.kind()) || value.target() == null
        || value.candidates() == null || (value.candidates().isEmpty() && value.fieldDescription() == null) || value.candidates().size() > 3
        || value.candidates().stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("请选择有效的标准匹配候选");
    }
    var source = prepare(value.target());
    if (!source.definition().equals(value.expectedDefinition())) throw new IllegalArgumentException("模型已改变，请重新生成");
    var choices = value.candidates().stream().map(c -> new StandardMatchProposal.Choice(c.standardId(), c.version(), c.reason())).toList();
    return validate(value.target(), source,
        new StandardMatchProposal(choices, java.util.List.of(), value.fieldDescription()), value.skillVersion(), value.skillHash());
  }

  private ModelSuggestionQueryApi modelApi() {
    var api = models.getIfAvailable(); if (api == null) throw new IllegalStateException("模型辅助读取未装配"); return api;
  }
  private StandardSuggestionQueryApi standardApi() {
    var api = standards.getIfAvailable(); if (api == null) throw new IllegalStateException("标准辅助读取未装配"); return api;
  }
}
