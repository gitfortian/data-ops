package io.yak.ops.business.agent.domain;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 技能领域值对象（管理面与运行时共享投影；truth owner {@code yak_agent_skill}）。
 *
 * @param skillId     技能标识（全局唯一，SkillBox 按此启停）
 * @param name        展示名
 * @param description 一句话描述（注入提示用）
 * @param metadata    能力标签等元数据（用于管理面展示，运行时只读 name/enabled/version）
 * @param content     技能正文（instructions，注入 System Prompt）
 * @param enabled     在线启停持久态（ENABLED/DISABLED）
 * @param version     乐观版本（并发编辑防静默覆盖）
 * @param createTime  创建时间
 * @param updateTime  更新时间
 */
public record AgentSkillBrief(
    String skillId,
    String name,
    String description,
    Map<String, Object> metadata,
    String content,
    boolean enabled,
    int version,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  /** 新技能（注册语义：启用态、版本 1）。 */
  public static AgentSkillBrief create(String skillId, String name, String description,
                                       Map<String, Object> metadata, String content) {
    LocalDateTime now = LocalDateTime.now();
    return new AgentSkillBrief(skillId, name, description, metadata, content, true, 1, now, now);
  }

  /** 更新快照（保留启停态与版本号，供乐观 CAS 校验）。 */
  public AgentSkillBrief updatedWith(String name, String description,
                                     Map<String, Object> metadata, String content) {
    return new AgentSkillBrief(
        skillId,
        name == null || name.isBlank() ? this.name : name,
        description == null || description.isBlank() ? this.description : description,
        metadata == null ? this.metadata : metadata,
        content == null || content.isBlank() ? this.content : content,
        enabled,
        version,
        createTime,
        LocalDateTime.now());
  }

  /** 更新成功后的版本自增与时间戳推进（调用方在 CAS 成功后才使用）。 */
  public AgentSkillBrief withBumpedVersion() {
    return new AgentSkillBrief(skillId, name, description, metadata, content, enabled,
        version + 1, createTime, LocalDateTime.now());
  }

  /** 在线启停后的状态快照（版本自增）。 */
  public AgentSkillBrief withEnabled(boolean enabled) {
    return new AgentSkillBrief(skillId, name, description, metadata, content, enabled,
        version + 1, createTime, LocalDateTime.now());
  }
}