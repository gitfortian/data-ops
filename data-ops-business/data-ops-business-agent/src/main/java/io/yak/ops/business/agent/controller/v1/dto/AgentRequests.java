package io.yak.ops.business.agent.controller.v1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Agent 对话 HTTP 入参集合。 */
public final class AgentRequests {

  /**
   * 轮次提交请求（提交/执行分离：仅入队，立即返回 turnId）。
   * message 与 toolResults 二选一：带 toolResults 视为 HITL 反问恢复（同一轮续跑）。
   *
   * @param sessionId 会话ID（前端生成，首次自动绑定归属）
   * @param message 用户消息（新提问时必填）
   * @param toolResults HITL 反问恢复时的工具应答
   */
  public record ChatTurnSubmitRequest(
      @NotBlank(message = "sessionId 不能为空") String sessionId,
      String message,
      List<ToolResultInput> toolResults) {

    public boolean isResume() {
      return toolResults != null && !toolResults.isEmpty();
    }

    public record ToolResultInput(String toolCallId, String toolName, String output) {}
  }

  /** 运行时配置更新（value 空串/null 回退种子默认值）。 */
  public record ConfigUpdateRequest(String value) {}

  /** 会话重命名。 */
  public record RenameSessionRequest(
      @NotBlank(message = "标题不能为空")
          @Size(max = 200, message = "标题长度不能超过 200")
          String title) {}

  /** 查询审计分页。 */
  public record QueryAuditPageRequest(
      @Min(value = 1, message = "页码必须大于 0") Long pageNo,
      @Min(value = 1, message = "每页条数必须大于 0")
          @Max(value = 200, message = "每页条数不能超过 200")
          Long pageSize,
      @Size(max = 64, message = "会话ID长度不能超过 64") String sessionId,
      Long datasetId) {

    public QueryAuditPageRequest {
      if (pageNo == null) {
        pageNo = 1L;
      }
      if (pageSize == null) {
        pageSize = 10L;
      }
    }
  }

  /** 报告分页查询。 */
  public record ReportPageRequest(
      @Min(value = 1, message = "页码必须大于 0") Long pageNo,
      @Min(value = 1, message = "每页条数必须大于 0")
          @Max(value = 200, message = "每页条数不能超过 200")
          Long pageSize,
      @Size(max = 100, message = "关键词长度不能超过 100") String keyword) {

    public ReportPageRequest {
      if (pageNo == null) {
        pageNo = 1L;
      }
      if (pageSize == null) {
        pageSize = 10L;
      }
    }
  }

  /** 技能保存请求（skills 在线管理：注册/更新共用）。 */
  public record SkillSaveRequest(
      @NotBlank(message = "技能标识不能为空") @Size(max = 64, message = "技能标识长度不能超过 64")
          String skillId,
      @NotBlank(message = "技能名称不能为空") @Size(max = 128, message = "技能名称长度不能超过 128")
          String name,
      @Size(max = 512, message = "技能描述长度不能超过 512") String description,
      @Size(max = 64, message = "技能标识长度不能超过 64") java.util.Map<String, Object> metadata,
      @NotBlank(message = "技能正文不能为空") String content) {}

  /** 技能在线启停请求。 */
  public record SkillActiveRequest(boolean active) {}

  private AgentRequests() {}
}
