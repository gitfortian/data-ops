package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.AgentSkillBrief;
import io.yak.ops.business.agent.repository.AgentSkillRepositoryAdapter;
import io.yak.ops.business.agent.runtime.AgentRuntime;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 技能在线管理稳定 Facade（skills 在线管理：注册/更新/查看/在线启停/删除）。
 *
 * <p>双写一致（方案 §2）：DB（yak_agent_skill）是持久真相（重启还原），SkillBox（运行时）
 * 是热真相（下一次推理即生效）；DB 更新失败即返回错误，不产生静默漂移。</p>
 *
 * <p>热路径键对齐（框架契约）：agentscope SkillBox 以 {@code AgentSkill.getSkillId()}
 * （name+source 派生的框架键）注册/启停/移除，与 DB skillId 不同——所有热操作必须用
 * 同一技能实例的派生键，否则 SkillBox 查不到（"Skill ID does not exist"）。</p>
 *
 * <p>热生效机制（§5）：DynamicSkillMiddleware 每次推理现读仓库（技能集签名变化重建 SkillBox、
 * 技能 instructions 追加进 System Prompt）。管理服务落库后，下一次推理即可见；</p>
 */
@Slf4j
@ConditionalOnAgentEnabled
@Service
@RequiredArgsConstructor
public class AgentSkillManageService {

  private final AgentSkillRepositoryAdapter skillRepository;
  private final AgentRuntime agentRuntime;

  /** 注册新技能：落库（ENABLED）+ 热注册；已存在抛冲突（409 语义）。 */
  public AgentSkillBrief register(String skillId, String name, String description,
                                  Map<String, Object> metadata, String content) {
    if (skillId == null || skillId.isBlank() || name == null || name.isBlank()
        || content == null || content.isBlank()) {
      throw new IllegalArgumentException("技能标识/名称/正文不能为空");
    }
    if (skillRepository.skillExists(skillId)) {
      throw new AgentSkillConflictException(
          "技能已存在，无法重复注册：" + skillId + "；solution=改用更新接口或换 skillId");
    }
    AgentSkillBrief brief = AgentSkillBrief.create(skillId, name, description, metadata, content);
    if (!skillRepository.save(brief, false)) {
      throw new AgentSkillConflictException(
          "技能已存在，无法重复注册：" + skillId + "；solution=改用更新接口或换 skillId");
    }
    hotRegister(brief);
    log.info("skill registered: skillId={}, name={}", skillId, name);
    return brief;
  }

  /** 更新技能（乐观版本：并发编辑丢 CAS 冲突 409）。覆写正文与元数据，启停态保持。 */
  public AgentSkillBrief update(String skillId, String name, String description,
                                Map<String, Object> metadata, String content) {
    return update(skillId, name, description, metadata, content, null);
  }

  public AgentSkillBrief update(String skillId, String name, String description,
      Map<String, Object> metadata, String content, Integer expectedVersion) {
    AgentSkillBrief current =
        skillRepository
            .findBySkillId(skillId)
            .orElseThrow(() -> new AgentSkillNotFoundException("技能不存在：" + skillId));
    if (expectedVersion != null && current.version() != expectedVersion) {
      throw new AgentSkillConflictException("技能版本已变更，请刷新后重新编辑");
    }
    AgentSkillBrief updated = current.updatedWith(name, description, metadata, content);
    if (!skillRepository.save(updated, true)) {
      throw new AgentSkillConflictException(
          "技能乐观版本冲突（并发编辑）：" + skillId + "；solution=刷新后重试");
    }
    AgentSkillBrief saved = updated.withBumpedVersion();
    hotSwap(saved);
    log.info("skill updated: skillId={}, version={}", skillId, saved.version());
    return saved;
  }

  /** 在线启停（热生效核心）：DB status + SkillBox.setSkillActive 双写。 */
  public void setActive(String skillId, boolean enabled) {
    AgentSkillBrief brief =
        skillRepository
            .findBySkillId(skillId)
            .orElseThrow(() -> new AgentSkillNotFoundException("技能不存在：" + skillId));
    skillRepository.updateEnabled(skillId, enabled);
    SkillBox skillBox = agentRuntime.getSkillBox();
    if (skillBox != null) {
      skillBox.setSkillActive(frameworkKey(skillId), enabled);
    }
    log.info("skill active switched: skillId={}, enabled={}（热生效，下一轮推理可见）", skillId, enabled);
  }

  /** 删除技能：DB 删除 + SkillBox 热卸。 */
  public void remove(String skillId) {
    AgentSkillBrief brief =
        skillRepository
            .findBySkillId(skillId)
            .orElseThrow(() -> new AgentSkillNotFoundException("技能不存在：" + skillId));
    String frameworkKey = frameworkKey(skillId);
    skillRepository.delete(skillId);
    SkillBox skillBox = agentRuntime.getSkillBox();
    if (skillBox != null && frameworkKey != null) {
      skillBox.removeSkill(frameworkKey);
    }
    log.info("skill removed: skillId={}", skillId);
  }

  /** 技能列表（含启停状态，管理面）。 */
  public List<AgentSkillBrief> list() {
    return skillRepository.listAll();
  }

  /** 技能详情。 */
  public AgentSkillBrief detail(String skillId) {
    return skillRepository
        .findBySkillId(skillId)
        .orElseThrow(() -> new AgentSkillNotFoundException("技能不存在：" + skillId));
  }

  // ---------------- 热路径辅助 ----------------

  /**
   * 框架键对齐：agentscope SkillBox 以 AgentSkill.getSkillId()（name+source 派生）为键；
   * 统一从 adapter 装载的权威实例取（与框架 reloadSkills 同源），避免自造实例键不一致。
   */
  private String frameworkKey(String skillId) {
    io.agentscope.core.skill.AgentSkill skill = skillRepository.getSkill(skillId);
    return skill == null ? null : skill.getSkillId();
  }

  private void hotRegister(AgentSkillBrief brief) {
    SkillBox skillBox = agentRuntime.getSkillBox();
    if (skillBox == null) {
      // 冷装配路径：SkillBox 尚未建立（模块未发生首次推理），仅落库；
      // doAssemble 冷还原会按 DB status 装载（方案 §2 冷还原路径）。
      return;
    }
    io.agentscope.core.skill.AgentSkill skill = skillRepository.getSkill(brief.skillId());
    if (skill == null) {
      return;
    }
    skillBox.registerSkill(skill);
    skillBox.setSkillActive(skill.getSkillId(), brief.enabled());
  }

  private void hotSwap(AgentSkillBrief brief) {
    SkillBox skillBox = agentRuntime.getSkillBox();
    if (skillBox == null) {
      return;
    }
    io.agentscope.core.skill.AgentSkill skill = skillRepository.getSkill(brief.skillId());
    if (skill == null) {
      return;
    }
    skillBox.removeSkill(skill.getSkillId());
    skillBox.registerSkill(skill);
    skillBox.setSkillActive(skill.getSkillId(), brief.enabled());
  }
}
