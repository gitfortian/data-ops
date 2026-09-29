package io.yak.ops.business.sync.offline.execution.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobLogEntry;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobLogPageResponse;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionLogPageVO;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineExecutionLogQueryTest {

  @Test
  void mergesYakOpsAndWorkerLogsByTimestamp() {
    OfflineExecutionEventRepository eventRepository = mock(OfflineExecutionEventRepository.class);
    LinkUpClient linkUpClient = mock(LinkUpClient.class);
    OfflineExecutionLogQuery query = new OfflineExecutionLogQuery(eventRepository, linkUpClient);

    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(3L);
    execution.setStatus("SUCCEEDED");
    execution.setExternalExecutionId("yak-offline-3");
    execution.setEngineJobId("flux-3");

    LocalDateTime eventTime = LocalDateTime.of(2026, 8, 6, 8, 53, 10, 100_000_000);
    OfflineExecutionEvent event =
        OfflineExecutionEvent.builder()
            .id(7L)
            .executionId(3L)
            .stateVersion(2L)
            .fromStatus("CREATED")
            .toStatus("SUBMITTED")
            .eventType("SUBMITTING")
            .message("正在向 Link-Up 提交 JobSpec")
            .createTime(eventTime)
            .build();
    when(eventRepository.listAfter(3L, 0L, 100)).thenReturn(List.of(event));
    when(eventRepository.listAfter(3L, 0L, 1000)).thenReturn(List.of(event));

    LinkUpJobLogEntry linkEntry = new LinkUpJobLogEntry();
    linkEntry.setSequence(12L);
    linkEntry.setTimestampMillis(
        eventTime.plusNanos(100_000_000).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    linkEntry.setLevel("INFO");
    linkEntry.setThread("link-up-job-1");
    linkEntry.setLogger("c.l.u.f.e.JobExecution");
    linkEntry.setMessage("Job started");

    LinkUpJobLogPageResponse linkPage = new LinkUpJobLogPageResponse();
    linkPage.setJobId("flux-3");
    linkPage.setExternalExecutionId("yak-offline-3");
    linkPage.setRunId("orders-1");
    linkPage.setItems(List.of(linkEntry));
    linkPage.setNextCursor(120L);
    linkPage.setCompleted(true);
    when(linkUpClient.logs("flux-3", 0L, 100)).thenReturn(linkPage);
    when(linkUpClient.logs("flux-3", 0L, 1000)).thenReturn(linkPage);

    OfflineExecutionLogPageVO result = query.logs(execution, "0:0", 100);

    assertEquals(2, result.getItems().size());
    assertEquals("YAK_OPS", result.getItems().get(0).getSource());
    assertEquals("LINK_UP", result.getItems().get(1).getSource());
    assertEquals("7:120", result.getNextCursor());
    assertTrue(result.isCompleted());
    assertTrue(result.isLinkUpAvailable());
    assertEquals("orders-1", result.getItems().get(1).getRunId());

    String text = query.text(execution);
    assertTrue(text.contains("2026-08-06 08:53:10.100"));
    assertTrue(text.contains("[YAK_OPS]"));
    assertTrue(text.contains("[LINK_UP]"));
  }
}
