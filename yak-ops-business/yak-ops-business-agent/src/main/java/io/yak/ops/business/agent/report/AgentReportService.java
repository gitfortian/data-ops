package io.yak.ops.business.agent.report;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.framework.common.PageData;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.agent.domain.ReportEntry;
import io.yak.ops.business.agent.repository.ReportRepository;
import io.yak.ops.business.agent.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 分析报告稳定入口：保存（工具 corridor）/ 分页 / 详情 / 删除。归属校验在仓储条件层强制执行。 */
@ConditionalOnAgentEnabled
@Service
@RequiredArgsConstructor
public class AgentReportService {

  private final ReportRepository reportRepository;
  private final SessionRepository sessionRepository;

  /** 由 save_analysis_report 工具调用：会话必须已存在，归属人取会话真相。 */
  public long saveFromTool(String sessionId, String title, String content) {
    var session = sessionRepository.findBySessionId(sessionId);
    if (session.isEmpty()) {
      throw new IllegalStateException("报告保存失败：会话不存在 " + sessionId);
    }
    return reportRepository.insert(session.get().userId(), sessionId, title, content);
  }

  public PageData<ReportEntry> page(long pageNo, long pageSize, String keyword) {
    return reportRepository.page(requireUserId(), keyword, (int) pageNo, (int) pageSize);
  }

  public ReportEntry detail(long reportId) {
    return reportRepository.findMeta(requireUserId(), reportId)
        .orElseThrow(() -> new IllegalArgumentException("报告不存在"));
  }

  /** 正文与元数据分两次读取，避免列表场景拖出大文本。 */
  public String content(long reportId) {
    // 先经元数据归属校验，再读正文，保证越权时同样返回"报告不存在"
    detail(reportId);
    return reportRepository.content(requireUserId(), reportId).orElseThrow();
  }

  public void delete(long reportId) {
    if (!reportRepository.softDelete(requireUserId(), reportId)) {
      throw new IllegalArgumentException("报告不存在");
    }
  }

  private static long requireUserId() {
    Long userId = YakSecurityContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalArgumentException("当前无登录用户上下文");
    }
    return userId;
  }
}
