package io.yak.ops.business.agent.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.dao.mapper.AgentSkillMapper;
import io.yak.ops.business.agent.dao.model.AgentSkillPO;
import io.yak.ops.business.agent.domain.AgentSkillBrief;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 智能体技能持久化适配器（skills 在线管理的 truth owner = {@code yak_agent_skill}）。
 *
 * <p>同时实现 agentscope {@link io.agentscope.core.skill.repository.AgentSkillRepository}：
 * DynamicSkillMiddleware / SkillBox 每次推理经此现读（技能集签名变化 -&gt; 重建 SkillBox -&gt; 提示注入），
 * 实现「在线注册/启停对下一次推理热生效」；并向管理面暴露技能登记/乐观更新/启停。</p>
 *
 * <p>契约：agentscope {@code AgentSkill} 的 {@code name}=skillId（SkillBox 键）、
 * 运行时 metadata 仅携带 {@code displayName}=展示名；启停和版本由 DB 拥有；
 * DB status 列在 PO 层映射为 ENABLED/DISABLED（持久真相），version 乐观锁在更新路径强制。</p>
 */
@Slf4j
@ConditionalOnAgentEnabled
@Repository
@RequiredArgsConstructor
public class AgentSkillRepositoryAdapter
    implements io.agentscope.core.skill.repository.AgentSkillRepository {

  private static final String META_DISPLAY_NAME = "displayName";
  private static final String META_ENABLED = "enabled";
  private static final String META_VERSION = "version";

  private final AgentSkillMapper skillMapper;

  // ---------------- agentscope 仓储接口（运行时现读） ----------------

  @Override
  public AgentSkill getSkill(String skillId) {
    return toAgentSkill(
        skillMapper.selectOne(
            Wrappers.lambdaQuery(AgentSkillPO.class).eq(AgentSkillPO::getSkillId, skillId)));
  }

  @Override
  public List<String> getAllSkillNames() {
    return skillMapper.selectList(null).stream().map(AgentSkillPO::getSkillId).toList();
  }

  @Override
  public List<AgentSkill> getAllSkills() {
    return skillMapper.selectList(null).stream()
        .filter(po -> "ENABLED".equals(po.getStatus()))
        .map(AgentSkillRepositoryAdapter::toAgentSkill)
        .toList();
  }

  /**
   * 保存（管理面写路径）。overwrite=false 时已存在即失败（防静默覆盖注册）；
   * overwrite=true 时更新必须带乐观版本条件（DB version 与传入一致才更新，否则返回 false）。
   *
   * @return 全部成功返回 true；任一冲突返回 false（调用方按 409 语义拒绝）
   */
  @Override
  public boolean save(List<AgentSkill> skills, boolean overwrite) {
    for (AgentSkill skill : skills) {
      AgentSkillPO po = toPO(skill);
      AgentSkillPO existing =
          skillMapper.selectOne(
              Wrappers.lambdaQuery(AgentSkillPO.class)
                  .eq(AgentSkillPO::getSkillId, po.getSkillId()));
      if (existing == null) {
        skillMapper.insert(po);
        continue;
      }
      if (!overwrite) {
        log.warn("skill already exists (overwrite=false): {}", po.getSkillId());
        return false;
      }
      // 乐观 CAS：仅当 DB version == 传入 version 才更新（防并发编辑静默覆盖；字符串 wrapper 规避
      // 测试环境 MyBatis-Plus lambda cache 依赖，生产列名与表结构同源）
      com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AgentSkillPO> wrapper =
          new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
      wrapper.eq("skill_id", po.getSkillId()).eq("version", po.getVersion());
      po.setVersion(po.getVersion() + 1);
      int rows = skillMapper.update(po, wrapper);
      if (rows == 0) {
        log.warn("skill optimistic version conflict: {}", po.getSkillId());
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean delete(String skillId) {
    return skillMapper.delete(
            Wrappers.lambdaQuery(AgentSkillPO.class).eq(AgentSkillPO::getSkillId, skillId))
        > 0;
  }

  @Override
  public boolean skillExists(String skillId) {
    return skillMapper.selectCount(
            Wrappers.lambdaQuery(AgentSkillPO.class).eq(AgentSkillPO::getSkillId, skillId))
        > 0;
  }

  @Override
  public AgentSkillRepositoryInfo getRepositoryInfo() {
    return new AgentSkillRepositoryInfo("db", "yak_agent_skill", true);
  }

  @Override
  public String getSource() {
    return "db:yak_agent_skill";
  }

  @Override
  public void setWriteable(boolean writable) {
    // yak_agent_skill 恒可写（管理服务唯一写入口）
  }

  @Override
  public boolean isWriteable() {
    return true;
  }

  // ---------------- 管理面辅助（稳定 Facade 使用） ----------------

  public Optional<AgentSkillBrief> findBySkillId(String skillId) {
    return Optional.ofNullable(
            skillMapper.selectOne(
                Wrappers.lambdaQuery(AgentSkillPO.class).eq(AgentSkillPO::getSkillId, skillId)))
        .map(AgentSkillRepositoryAdapter::toBrief);
  }

  public List<AgentSkillBrief> listAll() {
    return skillMapper.selectList(null).stream()
        .map(AgentSkillRepositoryAdapter::toBrief)
        .toList();
  }

  /** 在线启停：DB status 列更新（持久真相；乐观版本自增）。 */
  public void updateEnabled(String skillId, boolean enabled) {
    com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AgentSkillPO> wrapper =
        new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
    wrapper.eq("skill_id", skillId)
        .set("status", enabled ? "ENABLED" : "DISABLED")
        .setSql("version = version + 1");
    int rows = skillMapper.update(null, wrapper);
    if (rows == 0) {
      throw new IllegalArgumentException("技能不存在：" + skillId);
    }
  }

  /**
   * 管理面保存（稳定 Facade 使用）：brief 语义持久化（ENABLED 起始/乐观版本 CAS）。<br>
   * overwrite=false 且已存在 -> 返回 false（重复注册）；overwrite=true 且 version 不符 -> false（并发冲突）。
   */
  public boolean save(AgentSkillBrief brief, boolean overwrite) {
    AgentSkillPO po = toPO(brief);
    AgentSkillPO existing =
        skillMapper.selectOne(
            Wrappers.lambdaQuery(AgentSkillPO.class)
                .eq(AgentSkillPO::getSkillId, po.getSkillId()));
    if (existing == null) {
      if (overwrite) return false; // A concurrent delete must not resurrect a stale edit.
      skillMapper.insert(po);
      return true;
    }
    if (!overwrite) {
      return false;
    }
    int expectedVersion = po.getVersion();
    po.setVersion(expectedVersion + 1);
    int rows =
        skillMapper.update(
            po,
            new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AgentSkillPO>()
                .eq("skill_id", po.getSkillId())
                .eq("version", expectedVersion));
    return rows > 0;
  }

  private static AgentSkillPO toPO(AgentSkillBrief brief) {
    AgentSkillPO po = new AgentSkillPO();
    po.setSkillId(brief.skillId());
    po.setName(brief.name());
    po.setDescription(brief.description());
    po.setContent(brief.content());
    po.setStatus(brief.enabled() ? "ENABLED" : "DISABLED");
    po.setVersion(brief.version());
    po.setCreatedAt(java.time.LocalDateTime.now());
    po.setUpdatedAt(java.time.LocalDateTime.now());
    return po;
  }

  // ---------------- PO <-> agentscope AgentSkill / Brief 转换 ----------------

  private static AgentSkill toAgentSkill(AgentSkillPO po) {
    if (po == null) {
      return null;
    }
    // 运行时技能以逻辑 skillId 为框架 name，展示名放 displayName metadata（不能覆盖 SDK 保留的 name），启停状态（ENABLED/DISABLED）与乐观版本是
    // DB 持久真相，不作为模型指令字段；getAllSkills 过滤启用集合，变化驱动 SDK 重建。
    return AgentSkill.builder()
        .name(po.getSkillId())
        .description(po.getDescription())
        .putMetadata(META_DISPLAY_NAME, po.getName())
        .skillContent(po.getContent())
        .source("db:yak_agent_skill")
        .build();
  }

  private static AgentSkillPO toPO(AgentSkill skill) {
    AgentSkillPO po = new AgentSkillPO();
    po.setSkillId(skill.getName());
    Object name = skill.getMetadataValue(META_DISPLAY_NAME);
    po.setName(name == null ? skill.getName() : String.valueOf(name));
    po.setDescription(skill.getDescription());
    po.setContent(skill.getSkillContent());
    Object enabled = skill.getMetadataValue(META_ENABLED);
    boolean active = enabled == null || Boolean.parseBoolean(String.valueOf(enabled));
    po.setStatus(active ? "ENABLED" : "DISABLED");
    Object version = skill.getMetadataValue(META_VERSION);
    po.setVersion(version == null ? 1 : Integer.parseInt(String.valueOf(version)));
    return po;
  }

  private static AgentSkillBrief toBrief(AgentSkillPO po) {
    return new AgentSkillBrief(
        po.getSkillId(),
        po.getName(),
        po.getDescription(),
        Map.of(META_ENABLED, "ENABLED".equals(po.getStatus()), META_VERSION, po.getVersion()),
        po.getContent(),
        "ENABLED".equals(po.getStatus()),
        po.getVersion(),
        po.getCreatedAt(),
        po.getUpdatedAt());
  }
}
