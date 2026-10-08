package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioSkillScopeTest {
  static AgentSkill skill(String name, int version, String body) {
    return AgentSkill.builder().name(name).description("类型匹配").source("db:yak_agent_skill")
        .putMetadata("version", version).skillContent(body).build();
  }
  @Test void onlySelectedSkillCanLoadAndTheRepositoryIsReadOnly() {
    var repo = mock(AgentSkillRepository.class);
    var selected = skill("standard-match", 1, "正文");
    when(repo.getAllSkills()).thenReturn(List.of(selected, skill("other", 8, "无关")));
    var scope = new ScenarioSkillScope(repo, "standard-match");
    assertEquals(List.of("standard-match"), scope.getAllSkillNames());
    assertEquals("正文", scope.load(selected.getSkillId(), "SKILL.md"));
    assertThrows(IllegalArgumentException.class, () -> scope.load(selected.getSkillId(), "script.py"));
    assertThrows(IllegalArgumentException.class, () -> scope.load("other", "SKILL.md"));
    assertThrows(UnsupportedOperationException.class, () -> scope.save(List.of(selected), true));
  }
  @Test void editsDisablesDeletesAndSourceErrorsInvalidateAnExistingInvocation() {
    var repo = mock(AgentSkillRepository.class);
    var selected = skill("standard-match", 1, "正文");
    when(repo.getAllSkills()).thenReturn(List.of(selected));
    var scope = new ScenarioSkillScope(repo, "standard-match");
    when(repo.getAllSkills()).thenReturn(List.of(skill("standard-match", 1, "正文变化但版本不变")));
    assertThrows(IllegalStateException.class, scope::requireCurrent);
    when(repo.getAllSkills()).thenReturn(List.of());
    assertThrows(IllegalStateException.class, scope::requireCurrent);
    assertThrows(IllegalStateException.class, () -> new ScenarioSkillScope(repo, "standard-match"));
    when(repo.getAllSkills()).thenThrow(new IllegalStateException("down"));
    assertThrows(IllegalStateException.class, scope::requireCurrent);
  }
}
