package io.yak.ops.business.sync.offline.execution.query;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobLogEntry;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobLogPageResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpProtocolException;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpRequestException;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpTransportException;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionLogEntryVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionLogPageVO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 将 Yak Ops 状态事件和 Link-Up 物理日志合并成统一时间线。 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineExecutionLogQuery {

  private static final int MAX_TEXT_PAGES = 100;
  private static final int MAX_PAGE_SIZE = 1000;
  private static final String SOURCE_YAK_OPS = "YAK_OPS";
  private static final String SOURCE_LINK_UP = "LINK_UP";
  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  private final OfflineExecutionEventRepository eventRepository;
  private final LinkUpClient linkUpClient;

  public OfflineExecutionLogQuery(
      OfflineExecutionEventRepository eventRepository,
      LinkUpClient linkUpClient) {
    this.eventRepository = eventRepository;
    this.linkUpClient = linkUpClient;
  }

  public String text(OfflineJobExecution execution) {
    String cursor = Cursor.start().encode();
    String warning = null;
    StringBuilder result = new StringBuilder("# Yak Ops + Link-Up Unified Timeline\n");
    appendHeader(result, execution);

    for (int pageIndex = 0; pageIndex < MAX_TEXT_PAGES; pageIndex++) {
      OfflineExecutionLogPageVO page = logs(execution, cursor, MAX_PAGE_SIZE);
      warning = page.getWarning();
      appendPage(result, page);

      if (isLastPage(page, cursor)) {
        break;
      }
      cursor = page.getNextCursor();
    }

    if (StringUtils.hasText(warning)) {
      result.append("\n- WARN [YAK_OPS] [LOG_AGGREGATION] ")
          .append(warning)
          .append('\n');
    }
    return result.toString();
  }

  public OfflineExecutionLogPageVO logs(
      OfflineJobExecution execution,
      String cursorValue,
      int limit) {
    validateRequest(execution, limit);
    Cursor cursor = Cursor.parse(cursorValue);

    List<OfflineExecutionEvent> events =
        eventRepository.listAfter(execution.getId(), cursor.yakEventId(), limit);
    List<OfflineExecutionLogEntryVO> yakItems = toYakOpsEntries(execution, events);
    LinkUpLogBatch linkUp = fetchLinkUpLogs(execution, cursor.linkCursor(), limit);

    List<OfflineExecutionLogEntryVO> selected =
        mergeAndSelect(yakItems, linkUp.items(), limit);
    int selectedYakCount = countSource(selected, SOURCE_YAK_OPS);
    int selectedLinkCount = countSource(selected, SOURCE_LINK_UP);

    long nextYakEventId = nextYakEventId(cursor, yakItems, selectedYakCount);
    long nextLinkCursor = nextLinkCursor(cursor, linkUp, selectedLinkCount);
    boolean completed = isCompleted(
        execution,
        events,
        yakItems,
        selectedYakCount,
        linkUp,
        selectedLinkCount,
        limit);

    return OfflineExecutionLogPageVO.builder()
        .items(selected)
        .nextCursor(Cursor.encode(nextYakEventId, nextLinkCursor))
        .completed(completed)
        .linkUpAvailable(linkUp.available())
        .warning(linkUp.warning())
        .build();
  }

  private void validateRequest(OfflineJobExecution execution, int limit) {
    if (execution == null || execution.getId() == null) {
      throw new IllegalArgumentException("离线同步执行实例不能为空");
    }
    if (limit < 1 || limit > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("日志 limit 必须在 1 到 1000 之间");
    }
  }

  private List<OfflineExecutionLogEntryVO> toYakOpsEntries(
      OfflineJobExecution execution,
      List<OfflineExecutionEvent> events) {
    List<OfflineExecutionLogEntryVO> items = new ArrayList<>(events.size());
    for (OfflineExecutionEvent event : events) {
      items.add(toYakOpsEntry(execution, event));
    }
    return items;
  }

  private LinkUpLogBatch fetchLinkUpLogs(
      OfflineJobExecution execution,
      long cursor,
      int limit) {
    if (!StringUtils.hasText(execution.getEngineJobId())) {
      return LinkUpLogBatch.notRequired();
    }

    try {
      LinkUpJobLogPageResponse response =
          linkUpClient.logs(execution.getEngineJobId(), cursor, limit);
      List<OfflineExecutionLogEntryVO> items = new ArrayList<>();
      if (response != null && response.getItems() != null) {
        for (LinkUpJobLogEntry entry : response.getItems()) {
          items.add(toLinkUpEntry(execution, response, entry));
        }
      }
      return new LinkUpLogBatch(items, response, true, null);
    } catch (LinkUpRequestException exception) {
      String warning =
          exception.getStatusCode() == 404 || exception.getStatusCode() == 405
              ? "Link-Up 不支持任务日志接口，或该任务日志已不在 Worker 历史中"
              : "Link-Up 日志请求失败：" + exception.getMessage();
      return LinkUpLogBatch.unavailable(warning);
    } catch (LinkUpTransportException | LinkUpProtocolException exception) {
      return LinkUpLogBatch.unavailable("暂时无法读取 Link-Up 日志：" + exception.getMessage());
    }
  }

  private List<OfflineExecutionLogEntryVO> mergeAndSelect(
      List<OfflineExecutionLogEntryVO> yakItems,
      List<OfflineExecutionLogEntryVO> linkItems,
      int limit) {
    List<OfflineExecutionLogEntryVO> merged = new ArrayList<>(yakItems.size() + linkItems.size());
    merged.addAll(yakItems);
    merged.addAll(linkItems);
    merged.sort(logComparator());
    return new ArrayList<>(merged.subList(0, Math.min(limit, merged.size())));
  }

  private long nextYakEventId(
      Cursor cursor,
      List<OfflineExecutionLogEntryVO> yakItems,
      int selectedYakCount) {
    if (selectedYakCount == 0) {
      return cursor.yakEventId();
    }
    return yakItems.get(selectedYakCount - 1).getSequence();
  }

  private long nextLinkCursor(
      Cursor cursor,
      LinkUpLogBatch linkUp,
      int selectedLinkCount) {
    if (selectedLinkCount < linkUp.items().size()) {
      return linkUp.items().get(selectedLinkCount).getSequence();
    }
    if (linkUp.response() != null) {
      return value(linkUp.response().getNextCursor(), cursor.linkCursor());
    }
    return cursor.linkCursor();
  }

  private boolean isCompleted(
      OfflineJobExecution execution,
      List<OfflineExecutionEvent> events,
      List<OfflineExecutionLogEntryVO> yakItems,
      int selectedYakCount,
      LinkUpLogBatch linkUp,
      int selectedLinkCount,
      int limit) {
    boolean terminal = !OfflineExecutionStatus.isActive(execution.getStatus());
    boolean yakCompleted = selectedYakCount == yakItems.size() && events.size() < limit;
    boolean linkCompleted = linkCompleted(execution, terminal, linkUp, selectedLinkCount);
    return terminal && yakCompleted && linkCompleted;
  }

  private boolean linkCompleted(
      OfflineJobExecution execution,
      boolean terminal,
      LinkUpLogBatch linkUp,
      int selectedLinkCount) {
    if (!StringUtils.hasText(execution.getEngineJobId())) {
      return true;
    }
    if (!linkUp.available()) {
      return terminal;
    }
    return selectedLinkCount == linkUp.items().size()
        && linkUp.response() != null
        && Boolean.TRUE.equals(linkUp.response().getCompleted());
  }

  private boolean isLastPage(OfflineExecutionLogPageVO page, String currentCursor) {
    return page.isCompleted()
        || !StringUtils.hasText(page.getNextCursor())
        || page.getNextCursor().equals(currentCursor);
  }

  private void appendHeader(StringBuilder result, OfflineJobExecution execution) {
    result.append("executionId: ")
        .append(execution == null ? "-" : execution.getId())
        .append('\n')
        .append("externalExecutionId: ")
        .append(execution == null ? "-" : text(execution.getExternalExecutionId()))
        .append('\n')
        .append("engineJobId: ")
        .append(execution == null ? "-" : text(execution.getEngineJobId()))
        .append("\n\n");
  }

  private void appendPage(StringBuilder result, OfflineExecutionLogPageVO page) {
    if (page.getItems() == null) {
      return;
    }
    for (OfflineExecutionLogEntryVO item : page.getItems()) {
      appendTextLine(result, item);
    }
  }

  private Comparator<OfflineExecutionLogEntryVO> logComparator() {
    return Comparator
        .comparing(
            OfflineExecutionLogEntryVO::getTimestampMillis,
            Comparator.nullsLast(Long::compareTo))
        .thenComparing(
            OfflineExecutionLogEntryVO::getSource,
            Comparator.nullsLast(String::compareTo))
        .thenComparingLong(OfflineExecutionLogEntryVO::getSequence);
  }

  private int countSource(List<OfflineExecutionLogEntryVO> items, String source) {
    int result = 0;
    for (OfflineExecutionLogEntryVO item : items) {
      if (source.equals(item.getSource())) {
        result++;
      }
    }
    return result;
  }

  private void appendTextLine(StringBuilder result, OfflineExecutionLogEntryVO item) {
    result.append(StringUtils.hasText(item.getTimestamp()) ? item.getTimestamp() : "-")
        .append(' ')
        .append(StringUtils.hasText(item.getLevel()) ? item.getLevel() : "INFO")
        .append(" [")
        .append(StringUtils.hasText(item.getSource()) ? item.getSource() : "UNKNOWN")
        .append(']');
    if (StringUtils.hasText(item.getStage())) {
      result.append(" [").append(item.getStage()).append(']');
    }
    result.append(' ')
        .append(StringUtils.hasText(item.getMessage()) ? item.getMessage() : "-")
        .append('\n');
  }

  private OfflineExecutionLogEntryVO toYakOpsEntry(
      OfflineJobExecution execution,
      OfflineExecutionEvent event) {
    Long timestampMillis = epochMillis(event.getCreateTime());
    String transition = text(event.getFromStatus()) + " -> " + text(event.getToStatus());
    String message =
        StringUtils.hasText(event.getMessage())
            ? transition + " | " + event.getMessage()
            : transition;

    return OfflineExecutionLogEntryVO.builder()
        .sequence(value(event.getId(), 0L))
        .timestampMillis(timestampMillis)
        .timestamp(format(timestampMillis))
        .source(SOURCE_YAK_OPS)
        .level(level(event.getToStatus()))
        .stage(event.getEventType())
        .externalExecutionId(execution.getExternalExecutionId())
        .engineJobId(execution.getEngineJobId())
        .message(message)
        .build();
  }

  private OfflineExecutionLogEntryVO toLinkUpEntry(
      OfflineJobExecution execution,
      LinkUpJobLogPageResponse response,
      LinkUpJobLogEntry entry) {
    Long timestampMillis = entry == null ? null : entry.getTimestampMillis();
    String logger = entry == null ? null : entry.getLogger();
    String message = entry == null ? null : entry.getMessage();

    return OfflineExecutionLogEntryVO.builder()
        .sequence(entry == null ? 0L : value(entry.getSequence(), 0L))
        .timestampMillis(timestampMillis)
        .timestamp(format(timestampMillis))
        .source(SOURCE_LINK_UP)
        .level(
            entry == null || !StringUtils.hasText(entry.getLevel())
                ? "INFO"
                : entry.getLevel().toUpperCase(Locale.ROOT))
        .stage(linkStage(logger, message))
        .externalExecutionId(
            firstText(
                response == null ? null : response.getExternalExecutionId(),
                execution.getExternalExecutionId()))
        .engineJobId(
            firstText(
                response == null ? null : response.getJobId(),
                execution.getEngineJobId()))
        .runId(response == null ? null : response.getRunId())
        .thread(entry == null ? null : entry.getThread())
        .logger(logger)
        .message(message)
        .build();
  }

  private String linkStage(String logger, String message) {
    String normalizedLogger = logger == null ? "" : logger.toLowerCase(Locale.ROOT);
    String normalizedMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
    if (normalizedMessage.contains("catalog sql")
        || normalizedMessage.contains("create table")
        || normalizedLogger.contains("catalog")) {
      return "SCHEMA";
    }
    if (normalizedLogger.contains("taskexecutor")) {
      return "TASK";
    }
    if (normalizedLogger.contains("jobexecution")) {
      return "JOB";
    }
    if (normalizedLogger.contains("split")) {
      return "SPLIT";
    }
    return "ENGINE";
  }

  private String level(String status) {
    if (!StringUtils.hasText(status)) {
      return "INFO";
    }
    String normalized = status.trim().toUpperCase(Locale.ROOT);
    if ("FAILED".equals(normalized) || "LOST".equals(normalized)) {
      return "ERROR";
    }
    if ("CANCELED".equals(normalized) || "CANCELLED".equals(normalized)) {
      return "WARN";
    }
    return "INFO";
  }

  private Long epochMillis(LocalDateTime value) {
    return value == null
        ? null
        : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  private String format(Long timestampMillis) {
    return timestampMillis == null
        ? null
        : FORMAT.format(Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()));
  }

  private String text(String value) {
    return StringUtils.hasText(value) ? value : "-";
  }

  private String firstText(String first, String second) {
    return StringUtils.hasText(first) ? first : second;
  }

  private long value(Long value, long fallback) {
    return value == null ? fallback : value;
  }

  private record LinkUpLogBatch(
      List<OfflineExecutionLogEntryVO> items,
      LinkUpJobLogPageResponse response,
      boolean available,
      String warning) {

    private static LinkUpLogBatch notRequired() {
      return new LinkUpLogBatch(List.of(), null, true, null);
    }

    private static LinkUpLogBatch unavailable(String warning) {
      return new LinkUpLogBatch(List.of(), null, false, warning);
    }
  }

  private record Cursor(long yakEventId, long linkCursor) {

    private static Cursor start() {
      return new Cursor(0L, 0L);
    }

    private static Cursor parse(String value) {
      if (!StringUtils.hasText(value)) {
        return start();
      }

      String[] parts = value.trim().split(":", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("日志 cursor 格式不正确");
      }
      try {
        long yakEventId = Long.parseLong(parts[0]);
        long linkCursor = Long.parseLong(parts[1]);
        if (yakEventId < 0L || linkCursor < 0L) {
          throw new IllegalArgumentException("日志 cursor 不能为负数");
        }
        return new Cursor(yakEventId, linkCursor);
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("日志 cursor 格式不正确", exception);
      }
    }

    private String encode() {
      return encode(yakEventId, linkCursor);
    }

    private static String encode(long yakEventId, long linkCursor) {
      return yakEventId + ":" + linkCursor;
    }
  }
}
