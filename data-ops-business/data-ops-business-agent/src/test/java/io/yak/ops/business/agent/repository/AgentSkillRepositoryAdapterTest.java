package io.yak.ops.business.agent.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import io.yak.ops.business.agent.dao.mapper.AgentSkillMapper;
import io.yak.ops.business.agent.dao.model.AgentSkillPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 技能持久化适配器功能测试（SKILL-REPO 系列）：agentscope 接口桥接、
 * 注册/查询/乐观版本更新/在线启停/删除/幂等语义。
 * mapper 为替身，验证 PO <-> agentscope AgentSkill 的双向映射与 DB 语义。
 */
class AgentSkillRepositoryAdapterTest {

  private AgentSkillMapper skillMapper;
  private AgentSkillRepositoryAdapter adapter;

  @BeforeEach
  void setUp() {
    skillMapper = mock(AgentSkillMapper.class);
    adapter = new AgentSkillRepositoryAdapter(skillMapper);
  }

  private static AgentSkillPO po(String skillId, boolean enabled, int version) {
    AgentSkillPO po = new AgentSkillPO();
    // 契约：agentscope name=skillId（SkillBox 键）——PO.name 与 skillId 一致（展示名存 metadata 场景不在本映射范围）
    po.setSkillId(skillId);
    po.setName(skillId);
    po.setDescription("描述-" + skillId);
    po.setContent("正文-" + skillId);
    po.setStatus(enabled ? "ENABLED" : "DISABLED");
    po.setVersion(version);
    po.setCreatedAt(LocalDateTime.now());
    po.setUpdatedAt(LocalDateTime.now());
    return po;
  }

  @Test
  void bridgesMysqlSkillIntoAgentscopeValueObject() {
    when(skillMapper.selectOne(any(Wrapper.class))).thenReturn(po("asset-yoy", true, 1));

    AgentSkill skill = adapter.getSkill("asset-yoy");

    assertNotNull(skill);
    assertEquals("asset-yoy", skill.getName(), "agentscope name=skillId（SkillBox 键）");
    assertEquals("描述-asset-yoy", skill.getDescription());
    assertEquals("正文-asset-yoy", skill.getSkillContent());
    assertEquals("asset-yoy", skill.getMetadataValue("name"), "metadata 携带展示名（name==skillId 契约）");
    // 契约（框架实测）：启停状态与乐观版本是 DB 持久真相，不进入运行时技能 metadata
    // （否则改 DB 启停触发 reloadSkills 签名变化、SkillBox 重建，热停用丢失）
    assertEquals(null, skill.getMetadataValue("enabled"), "启停态不得进入运行时技能 metadata");
    assertEquals(null, skill.getMetadataValue("version"), "乐观版本不得进入运行时技能 metadata");
    assertTrue(skill.getSkillId().contains("asset-yoy"), "框架派生键含技能标识（name+source）");
  }

  @Test
  void listAllMapsEnabledAndDisabledSkills() {
    AgentSkillPO enabled = po("a", true, 1);
    AgentSkillPO disabled = po("b", false, 2);
    when(skillMapper.selectList(any())).thenReturn(List.of(enabled, disabled));

    List<io.yak.ops.business.agent.domain.AgentSkillBrief> briefs = adapter.listAll();

    assertEquals(2, briefs.size());
    assertTrue(briefs.get(0).enabled());
    assertFalse(briefs.get(1).enabled());
    assertEquals(2, briefs.get(1).version());
  }

  @Test
  void saveWithOverwriteFalseRejectsDuplicate() {
    when(skillMapper.selectOne(any(Wrapper.class))).thenReturn(po("asset-yoy", true, 1));

    boolean result =
        adapter.save(
            List.of(AgentSkill.builder().name("asset-yoy").description("d")
                .putMetadata("enabled", true).putMetadata("version", 1)
                .skillContent("c").build()),
            false);

    assertFalse(result, "overwrite=false 且已存在必须拒绝（防静默覆盖注册）");
  }

  @Test
  void saveWithoutExistingInserts() {
    when(skillMapper.selectOne(any(Wrapper.class))).thenReturn(null);

    boolean result =
        adapter.save(
            List.of(AgentSkill.builder().name("new-skill").description("d")
                .putMetadata("enabled", true).putMetadata("version", 1)
                .skillContent("c").build()),
            false);

    assertTrue(result);
    verify(skillMapper).insert(any(AgentSkillPO.class));
  }

  @Test
  void saveWithOverwriteTrueButVersionConflictRejected() {
    // DB 现有版本 1，传入版本 1，但 update 条件版本不匹配（模拟并发：另一事务已推进版本）
    when(skillMapper.selectOne(any(Wrapper.class))).thenReturn(po("asset-yoy", true, 1));
    when(skillMapper.update(any(AgentSkillPO.class), any(Wrapper.class))).thenReturn(0);

    boolean result =
        adapter.save(
            List.of(AgentSkill.builder().name("asset-yoy").description("d")
                .putMetadata("enabled", true).putMetadata("version", 1)
                .skillContent("新正文").build()),
            true);

    assertFalse(result, "乐观 CAS 失败必须返回 false（调用方转 409 语义）");
  }

  @Test
  void updateEnabledSwitchesStatusColumn() {
    when(skillMapper.update(any(), any(Wrapper.class))).thenReturn(1);

    adapter.updateEnabled("asset-yoy", false);

    // mapper 替身无法断言 SQL set 内容；由 updateEnabled 的契约（status 列切换 + 行数守卫）覆盖
    verify(skillMapper).update(any(), any(Wrapper.class));
  }

  @Test
  void updateEnabledOnMissingThrows() {
    when(skillMapper.update(any(), any(Wrapper.class))).thenReturn(0);

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> adapter.updateEnabled("missing", false));
  }

  @Test
  void deleteReturnsAffectedRows() {
    when(skillMapper.delete(any(Wrapper.class))).thenReturn(1);
    assertTrue(adapter.delete("asset-yoy"));
    when(skillMapper.delete(any(Wrapper.class))).thenReturn(0);
    assertFalse(adapter.delete("asset-yoy"));
  }

  @Test
  void skillExistsUsesCount() {
    when(skillMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
    assertTrue(adapter.skillExists("a"));
    when(skillMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
    assertFalse(adapter.skillExists("b"));
  }

  @Test
  void repositoryInfoAndSourceDeclared() {
    AgentSkillRepositoryInfo info = adapter.getRepositoryInfo();
    assertEquals("db", info.getType());
    assertEquals("yak_agent_skill", info.getLocation());
    assertTrue(info.isWritable());
    assertEquals("db:yak_agent_skill", adapter.getSource());
    assertTrue(adapter.isWriteable());
  }

  @Test
  void toBriefNullSafe() {
    assertNull(adapter.getSkill("missing"));
    assertTrue(adapter.findBySkillId("missing").isEmpty());
  }
}