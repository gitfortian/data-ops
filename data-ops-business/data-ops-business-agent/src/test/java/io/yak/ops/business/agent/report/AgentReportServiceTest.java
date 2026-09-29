package io.yak.ops.business.agent.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.agent.domain.ReportEntry;
import io.yak.ops.business.agent.repository.ReportRepository;
import io.yak.ops.business.agent.repository.SessionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 报告 Facade 行为测试：保存归属解析、越权即"报告不存在"、删除作用域。 */
class AgentReportServiceTest {

  private ReportRepository reportRepository;
  private SessionRepository sessionRepository;
  private AgentReportService reportService;

  @BeforeEach
  void setUp() {
    reportRepository = mock(ReportRepository.class);
    sessionRepository = mock(SessionRepository.class);
    reportService = new AgentReportService(reportRepository, sessionRepository);
  }

  @Test
  void saveFromToolResolvesOwnerFromSessionTruth() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new io.yak.ops.business.agent.domain.SessionMeta("s1", 42L, 1L, null, null, null)));
    when(reportRepository.insert(anyLong(), anyString(), anyString(), anyString())).thenReturn(9L);

    long id = reportService.saveFromTool("s1", "标题", "正文");

    assertEquals(9L, id);
    verify(reportRepository).insert(42L, "s1", "标题", "正文");
  }

  @Test
  void saveWithoutSessionIsRejected() {
    when(sessionRepository.findBySessionId("ghost")).thenReturn(Optional.empty());

    assertThrows(IllegalStateException.class, () -> reportService.saveFromTool("ghost", "t", "c"));
  }

  @Test
  void detailOfOtherUserReportsAsMissing() {
    when(reportRepository.findMeta(anyLong(), eq(5L))).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> reportService.detail(5L));
  }

  @Test
  void deleteFallsBackToMissingWhenNoRowAffected() {
    when(reportRepository.softDelete(anyLong(), eq(6L))).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> reportService.delete(6L));
  }

  @Test
  void pagePassesCurrentUserScope() {
    try (var mocked = org.mockito.Mockito.mockStatic(io.yak.framework.security.context.YakSecurityContext.class)) {
      mocked.when(io.yak.framework.security.context.YakSecurityContext::getCurrentUserId).thenReturn(42L);
      when(reportRepository.page(anyLong(), any(), anyInt(), anyInt()))
          .thenReturn(PageData.of(java.util.List.of(), 0, 1, 10));

      reportService.page(1, 10, "kw");

      verify(reportRepository).page(42L, "kw", 1, 10);
    }
  }
}
