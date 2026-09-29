package io.yak.ops.business.agent.conversation;

/** 技能不存在（404 语义）：技能管理操作指向不存在的 skillId。 */
public class AgentSkillNotFoundException extends RuntimeException {

  public AgentSkillNotFoundException(String message) {
    super(message);
  }
}