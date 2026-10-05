package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.agent.domain.AgentSkillBrief;
import io.yak.ops.business.agent.repository.AgentSkillRepositoryAdapter;
import io.yak.ops.business.agent.runtime.AgentRuntime;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 技能在线管理服务功能测试（SKILL-MGMT 系列）：
 * 注册/重复注册冲突/更新乐观版本/在线启停双写/删除/404 语义。
 * 仓库与 SkillBox 均为替身，验证管理面的领域语义（持久真相 DB + 热路径双写）。
 */
class AgentSkillManageServiceTest {

  private AgentSkillRepositoryAdapter skillRepository;
  private AgentRuntime agentRuntime;
  private AgentSkillManageService service;


  private static io.agentscope.core.skill.AgentSkill agentscopeSkill(String skillId) {
    return io.agentscope.core.skill.AgentSkill.builder()
        .name(skillId)
        .description("desc-" + skillId)
        .putMetadata("enabled", true)
        .putMetadata("version", 1)
        .skillContent("content-" + skillId)
        .source("db:yak_agent_skill")
        .build();
  }

  @BeforeEach
  void setUp() {
    skillRepository = mock(AgentSkillRepositoryAdapter.class);
    agentRuntime = mock(AgentRuntime.class);
    service = new AgentSkillManageService(skillRepository, agentRuntime);
  }

  @Test
  void registerPersistsAndHotRegisters() {
    when(skillRepository.skillExists("asset-yoy")).thenReturn(false);
    when(skillRepository.getSkill("asset-yoy")).thenReturn(agentscopeSkill("asset-yoy"));
    when(skillRepository.save(any(io.yak.ops.business.agent.domain.AgentSkillBrief.class), eq(false)))
        .thenReturn(true);
    when(agentRuntime.getSkillBox()).thenReturn(mock(SkillBox.class));

    AgentSkillBrief brief = service.register("asset-yoy", "资产管理同比分析", "对固定资产做同比分析",
        Map.of("category", "asset"), "你擅长对固定资产按同比口径分析。");

    assertEquals("asset-yoy", brief.skillId());
    assertTrue(brief.enabled(), "新技能默认启用");
    assertEquals(1, brief.version());
    // 双写：DB upsert（非覆盖）+ 热注册
    verify(skillRepository).save(any(io.yak.ops.business.agent.domain.AgentSkillBrief.class), eq(false));
    verify(agentRuntime.getSkillBox()).registerSkill(any(AgentSkill.class));
    // frameworkKey 为 agentscope 派生键（name+source），断言不绑定具体值（框架内部契约）
    verify(agentRuntime.getSkillBox()).setSkillActive(anyString(), anyBoolean());
  }

  @Test
  void registerDuplicateRejectedAsConflict() {
    when(skillRepository.skillExists("asset-yoy")).thenReturn(true);

    assertThrows(AgentSkillConflictException.class,
        () -> service.register("asset-yoy", "资产管理同比分析", "desc", Map.of(), "content"));
  }

  @Test
  void updateBumpsVersionWithCacheAfterCas() {
    AgentSkillBrief existing = AgentSkillBrief.create("asset-yoy", "资产管理同比分析", "desc",
        Map.of(), "旧正文");
    when(skillRepository.findBySkillId("asset-yoy")).thenReturn(Optional.of(existing));
    when(skillRepository.save(any(io.yak.ops.business.agent.domain.AgentSkillBrief.class), eq(true))).thenReturn(true);
    when(agentRuntime.getSkillBox()).thenReturn(mock(SkillBox.class));

    AgentSkillBrief updated = service.update("asset-yoy", "资产管理同比分析", "desc", Map.of(), "新正文");

    assertEquals("新正文", updated.content());
    assertEquals(existing.version() + 1, updated.version(), "响应必须返回持久化推进后的版本");
    verify(skillRepository).save(any(io.yak.ops.business.agent.domain.AgentSkillBrief.class), eq(true));
  }

  @Test
  void updateCasConflictRejected() {
    AgentSkillBrief existing = AgentSkillBrief.create("asset-yoy", "资产管理同比分析", "desc",
        Map.of(), "旧正文");
    when(skillRepository.findBySkillId("asset-yoy")).thenReturn(Optional.of(existing));
    // CAS 失败（并发编辑丢乐观锁）
    when(skillRepository.save(any(io.yak.ops.business.agent.domain.AgentSkillBrief.class), eq(true))).thenReturn(false);

    assertThrows(AgentSkillConflictException.class,
        () -> service.update("asset-yoy", "资产管理同比分析", "desc", Map.of(), "并发新正文"));
  }

  @Test
  void setActiveWritesDbAndHotSwitches() {
    AgentSkillBrief existing = AgentSkillBrief.create("asset-yoy", "资产管理同比分析", "desc",
        Map.of(), "正文");
    when(skillRepository.findBySkillId("asset-yoy")).thenReturn(Optional.of(existing));
    when(skillRepository.getSkill("asset-yoy")).thenReturn(agentscopeSkill("asset-yoy"));
    when(agentRuntime.getSkillBox()).thenReturn(mock(SkillBox.class));

    service.setActive("asset-yoy", false);

    verify(skillRepository).updateEnabled("asset-yoy", false);
    verify(agentRuntime.getSkillBox()).setSkillActive(anyString(), anyBoolean());
  }

  @Test
  void setActiveOnMissingSkillIsNotFound() {
    when(skillRepository.findBySkillId("missing")).thenReturn(Optional.empty());

    assertThrows(AgentSkillNotFoundException.class, () -> service.setActive("missing", true));
  }

  @Test
  void removeDeletesDbAndHotUnregisters() {
    when(skillRepository.skillExists("asset-yoy")).thenReturn(true);
    when(skillRepository.findBySkillId("asset-yoy"))
        .thenReturn(Optional.of(
            AgentSkillBrief.create("asset-yoy", "资产管理同比分析", "desc", Map.of(), "正文")));
    when(skillRepository.getSkill("asset-yoy")).thenReturn(agentscopeSkill("asset-yoy"));
    when(agentRuntime.getSkillBox()).thenReturn(mock(SkillBox.class));

    service.remove("asset-yoy");

    verify(skillRepository).delete("asset-yoy");
    verify(agentRuntime.getSkillBox()).removeSkill(anyString());
  }

  @Test
  void listReturnsAllWithStatus() {
    AgentSkillBrief enabled = AgentSkillBrief.create("a", "甲", "d", Map.of(), "c");
    when(skillRepository.listAll()).thenReturn(List.of(enabled));

    List<AgentSkillBrief> skills = service.list();

    assertEquals(1, skills.size());
    assertEquals("a", skills.get(0).skillId());
    assertTrue(skills.get(0).enabled());
  }

  @Test
  void detailMissingIsNotFound() {
    when(skillRepository.findBySkillId("missing")).thenReturn(Optional.empty());

    assertThrows(AgentSkillNotFoundException.class, () -> service.detail("missing"));
  }

  @Test
  void registerWithoutSkillBoxColdPathStillPersists() {
    // 冷路径：SkillBox 尚未建立（模块未发生首次推理），仅落库，不抛错
    when(skillRepository.skillExists("cold-skill")).thenReturn(false);
    when(skillRepository.save(any(io.yak.ops.business.agent.domain.AgentSkillBrief.class), eq(false)))
        .thenReturn(true);
    when(agentRuntime.getSkillBox()).thenReturn(null);

    AgentSkillBrief brief = service.register("cold-skill", "冷启动技能", "d", Map.of(), "c");

    assertEquals("cold-skill", brief.skillId());
    verify(skillRepository).save(any(io.yak.ops.business.agent.domain.AgentSkillBrief.class), eq(false));
    assertFalse(brief.skillId().isBlank());
  }

  @Test void staleBrowserEditCannotOverwriteSkillChangedSinceLoad() {
    var current = AgentSkillBrief.create("a", "A", "d", Map.of(), "current").withBumpedVersion();
    when(skillRepository.findBySkillId("a")).thenReturn(Optional.of(current));
    assertThrows(AgentSkillConflictException.class, () -> service.update("a", "A", "d", Map.of(), "stale", 1));
    verify(skillRepository, org.mockito.Mockito.never()).save(any(AgentSkillBrief.class), anyBoolean());
    org.mockito.Mockito.verifyNoInteractions(agentRuntime);
  }
}
