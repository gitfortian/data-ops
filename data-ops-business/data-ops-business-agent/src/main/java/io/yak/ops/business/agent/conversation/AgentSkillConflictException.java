package io.yak.ops.business.agent.conversation;

/** 技能管理冲突（409 语义）：重复注册 / 乐观版本冲突（并发编辑防静默覆盖）。 */
public class AgentSkillConflictException extends RuntimeException {

  public AgentSkillConflictException(String message) {
    super(message);
  }
}