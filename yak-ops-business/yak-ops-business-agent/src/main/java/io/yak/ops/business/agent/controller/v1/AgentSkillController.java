package io.yak.ops.business.agent.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.agent.AgentPermissionCode;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.controller.v1.dto.AgentRequests.SkillActiveRequest;
import io.yak.ops.business.agent.controller.v1.dto.AgentRequests.SkillSaveRequest;
import io.yak.ops.business.agent.controller.v1.vo.AgentViews.SkillVO;
import io.yak.ops.business.agent.conversation.AgentSkillManageService;
import io.yak.ops.business.agent.domain.AgentSkillBrief;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;

/**
 * 技能在线管理接口：注册/更新/查看/在线启停/删除（skills，热生效）。
 * 热生效语义：DB（yak_agent_skill）为持久真相，下一次推理 DynamicSkillMiddleware 现读生效。
 */
@Tag(name = "AI 分析技能在线管理")
@RestController
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/skills")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class AgentSkillController {

  private final AgentSkillManageService skillManageService;

  @Operation(summary = "在线注册技能（下一轮推理即热生效）")
  @PostMapping
  @RequiresPermission(AgentPermissionCode.SKILL_MANAGE)
  public Result<SkillVO> register(@Valid @RequestBody SkillSaveRequest request) {
    AgentSkillBrief brief =
        skillManageService.register(
            request.skillId(),
            request.name(),
            request.description(),
            request.metadata(),
            request.content());
    return Result.success(toVO(brief));
  }

  @Operation(summary = "技能列表（含启用/停用状态）")
  @GetMapping
  @RequiresPermission(AgentPermissionCode.SKILL_READ)
  public Result<List<SkillVO>> list() {
    return Result.success(skillManageService.list().stream().map(AgentSkillController::toVO).toList());
  }

  @Operation(summary = "技能详情")
  @GetMapping("/{skillId}")
  @RequiresPermission(AgentPermissionCode.SKILL_READ)
  public Result<SkillVO> detail(@PathVariable String skillId) {
    return Result.success(toVO(skillManageService.detail(skillId)));
  }

  @Operation(summary = "更新技能（乐观版本，并发编辑冲突返回 409）")
  @PutMapping("/{skillId}")
  @RequiresPermission(AgentPermissionCode.SKILL_MANAGE)
  public Result<SkillVO> update(
      @PathVariable String skillId, @Valid @RequestBody SkillSaveRequest request) {
    AgentSkillBrief brief =
        skillManageService.update(
            skillId,
            request.name(),
            request.description(),
            request.metadata(),
            request.content());
    return Result.success(toVO(brief));
  }

  @Operation(summary = "在线启用/停用技能（热生效，下一轮推理可见）")
  @PutMapping("/{skillId}/active")
  @RequiresPermission(AgentPermissionCode.SKILL_MANAGE)
  public Result<Boolean> setActive(
      @PathVariable String skillId, @Valid @RequestBody SkillActiveRequest request) {
    skillManageService.setActive(skillId, request.active());
    return Result.success(true);
  }

  @Operation(summary = "删除技能（热卸）")
  @DeleteMapping("/{skillId}")
  @RequiresPermission(AgentPermissionCode.SKILL_MANAGE)
  public Result<Boolean> remove(@PathVariable String skillId) {
    skillManageService.remove(skillId);
    return Result.success(true);
  }

  private static SkillVO toVO(AgentSkillBrief brief) {
    return new SkillVO(
        brief.skillId(),
        brief.name(),
        brief.description(),
        brief.metadata(),
        brief.content(),
        brief.enabled(),
        brief.version(),
        brief.createTime(),
        brief.updateTime());
  }
}