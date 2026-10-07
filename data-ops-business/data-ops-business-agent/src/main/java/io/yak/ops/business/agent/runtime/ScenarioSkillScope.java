package io.yak.ops.business.agent.runtime;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import java.util.List;

/** Invocation view of the managed catalog; it cannot register or activate another skill. */
final class ScenarioSkillScope implements AgentSkillRepository {
  private final AgentSkillRepository source;
  private final AgentSkill skill;
  private final int version;
  private final String hash;

  ScenarioSkillScope(AgentSkillRepository source, String name) {
    if (source == null) throw new IllegalStateException("[SKILL_NOT_AVAILABLE] 请先配置并启用场景 Skill");
    this.source = source;
    var selected = source.getAllSkills().stream().filter(s -> name.equals(s.getName())).findFirst()
        .orElseThrow(() -> new IllegalStateException("[SKILL_NOT_AVAILABLE] 场景 Skill 尚未启用"));
    // Only text participates in this scenario; resources/origin paths never reach the SDK uploader.
    this.skill = AgentSkill.builder().name(selected.getName()).source(selected.getSource())
        .description(selected.getDescription()).skillContent(selected.getSkillContent())
        .putMetadata("version", selected.getMetadataValue("version")).build();
    Object value = skill.getMetadataValue("version");
    if (!(value instanceof Number number) || number.intValue() < 1) {
      throw new IllegalStateException("[SKILL_NOT_AVAILABLE] 场景 Skill 缺少管理版本");
    }
    this.version = number.intValue();
    this.hash = fingerprint(skill);
  }

  int version() { return version; }
  String hash() { return hash; }
  void requireCurrent() {
    var current = source.getAllSkills().stream().filter(s -> skill.getName().equals(s.getName())).findFirst()
        .orElseThrow(() -> new IllegalStateException("[SKILL_CHANGED] 场景 Skill 已停用或删除，请重新生成"));
    if (!hash.equals(fingerprint(current))) throw new IllegalStateException("[SKILL_CHANGED] 场景 Skill 已更新，请重新生成");
  }
  String load(String id, String path) {
    requireCurrent();
    if (!skill.getSkillId().equals(id) || !"SKILL.md".equals(path)) {
      throw new IllegalArgumentException("[SKILL_NOT_AVAILABLE] 不允许读取任务外技能或资源");
    }
    return skill.getSkillContent();
  }
  private static String fingerprint(AgentSkill value) {
    return RuntimeContractHash.hash(value.getName() + "\u0000" + value.getSource() + "\u0000"
        + value.getDescription() + "\u0000" + value.getMetadataValue("version") + "\u0000" + value.getSkillContent());
  }
  @Override public AgentSkill getSkill(String id) { return skill.getName().equals(id) ? skill : null; }
  @Override public List<String> getAllSkillNames() { return List.of(skill.getName()); }
  @Override public List<AgentSkill> getAllSkills() { requireCurrent(); return List.of(skill); }
  @Override public boolean skillExists(String id) { return skill.getName().equals(id); }
  @Override public AgentSkillRepositoryInfo getRepositoryInfo() { return new AgentSkillRepositoryInfo("invocation", "managed-scenario", false); }
  @Override public String getSource() { return source.getSource(); }
  @Override public void setWriteable(boolean value) { if (value) throw new UnsupportedOperationException("场景技能只读"); }
  @Override public boolean isWriteable() { return false; }
  @Override public boolean save(List<AgentSkill> skills, boolean overwrite) { throw new UnsupportedOperationException("场景技能只读"); }
  @Override public boolean delete(String id) { throw new UnsupportedOperationException("场景技能只读"); }
}
