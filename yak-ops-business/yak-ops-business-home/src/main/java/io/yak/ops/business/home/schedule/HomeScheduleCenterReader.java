package io.yak.ops.business.home.schedule;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleStatus;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.framework.schedule.api.TriggerType;
import io.yak.ops.common.schedule.YakScheduleGateway;
import io.yak.ops.common.schedule.YakScheduleNamespaces;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.quartz.CronExpression;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 首页调度中心只读投影：只读取 Yak Schedule 统一运行时快照。 */
@Component
public class HomeScheduleCenterReader {

  private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter ONE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final YakScheduleGateway offlineSchedules;
  private final YakScheduleGateway workflowSchedules;
  private final YakScheduleGateway qualitySchedules;

  public HomeScheduleCenterReader(ObjectProvider<ScheduleManager> scheduleManagers) {
    this.offlineSchedules = new YakScheduleGateway(
        scheduleManagers::getIfAvailable, YakScheduleNamespaces.OFFLINE_SYNC);
    this.workflowSchedules = new YakScheduleGateway(
        scheduleManagers::getIfAvailable, YakScheduleNamespaces.WORKFLOW);
    this.qualitySchedules = new YakScheduleGateway(
        scheduleManagers::getIfAvailable, YakScheduleNamespaces.DATA_QUALITY);
  }

  public CalendarResponse calendar(String monthValue) {
    YearMonth month = resolveMonth(monthValue);
    LocalDate monthStart = month.atDay(1);
    LocalDate monthEnd = month.plusMonths(1).atDay(1);

    List<UnifiedScheduleTask> tasks = new ArrayList<>();
    tasks.addAll(tasks(offlineSchedules, "OFFLINE_SYNC"));
    tasks.addAll(tasks(workflowSchedules, "WORKFLOW"));
    tasks.addAll(tasks(qualitySchedules, "DATA_QUALITY"));

    Map<LocalDate, List<ScheduleOccurrence>> occurrencesByDay = new LinkedHashMap<>();
    Map<String, ScheduleSummary> summariesByTask = new HashMap<>();

    for (UnifiedScheduleTask task : tasks) {
      List<ScheduleOccurrenceAt> occurrences = occurrences(task, monthStart, monthEnd);
      if (occurrences.isEmpty()) continue;

      ScheduleOccurrenceAt first = occurrences.get(0);
      summariesByTask.put(
          task.key(),
          new ScheduleSummary(
              task.taskId(),
              task.taskType(),
              task.taskName(),
              task.scheduleText(),
              first.dateTime().toLocalDate().toString(),
              first.dateTime().format(TIME_FORMATTER),
              task.detailPath()));

      for (ScheduleOccurrenceAt occurrence : occurrences) {
        ScheduleOccurrence item = new ScheduleOccurrence(
            task.taskId(),
            task.taskType(),
            task.taskName(),
            occurrence.dateTime().format(TIME_FORMATTER),
            task.scheduleText(),
            task.detailPath());
        occurrencesByDay.computeIfAbsent(occurrence.dateTime().toLocalDate(), ignored -> new ArrayList<>())
            .add(item);
      }
    }

    List<DaySchedule> days = occurrencesByDay.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> {
          List<ScheduleOccurrence> sorted = entry.getValue().stream()
              .sorted(Comparator
                  .comparing(ScheduleOccurrence::time)
                  .thenComparing(ScheduleOccurrence::taskType)
                  .thenComparing(ScheduleOccurrence::taskName))
              .toList();
          List<ScheduleOccurrence> preview = sorted.size() > 3 ? sorted.subList(0, 3) : sorted;
          return new DaySchedule(entry.getKey().toString(), sorted.size(), preview);
        })
        .toList();

    List<ScheduleSummary> overview = summariesByTask.values().stream()
        .sorted(Comparator
            .comparing(ScheduleSummary::nextRunDate)
            .thenComparing(ScheduleSummary::nextRunTime)
            .thenComparing(ScheduleSummary::taskName))
        .limit(3)
        .toList();

    return new CalendarResponse(
        month.format(MONTH_FORMATTER),
        summariesByTask.size(),
        days,
        overview);
  }

  private List<UnifiedScheduleTask> tasks(YakScheduleGateway gateway, String taskType) {
    return gateway.list().stream()
        .filter(snapshot -> snapshot.status() == ScheduleStatus.ENABLED)
        .filter(snapshot -> snapshot.definition() != null && snapshot.definition().enabled())
        .map(snapshot -> task(snapshot, taskType))
        .filter(task -> task != null)
        .toList();
  }

  private UnifiedScheduleTask task(ScheduleSnapshot snapshot, String taskType) {
    ScheduleDefinition definition = snapshot.definition();
    ScheduleTrigger trigger = definition.trigger();
    if (trigger == null) return null;

    String taskId = definition.key().name();
    ZoneId zoneId = trigger.type() == TriggerType.CRON
        ? trigger.zoneId()
        : ZoneId.systemDefault();
    return new UnifiedScheduleTask(
        taskId,
        taskType,
        defaultText(definition.description(), fallbackName(taskType, taskId)),
        trigger,
        zoneId,
        metadataInstant(definition.metadata(), "startTime"),
        metadataInstant(definition.metadata(), "endTime"),
        scheduleText(trigger, zoneId),
        detailPath(taskType, taskId));
  }

  private List<ScheduleOccurrenceAt> occurrences(
      UnifiedScheduleTask task,
      LocalDate monthStart,
      LocalDate monthEnd) {
    Instant lowerBound = monthStart.atStartOfDay(task.zoneId()).toInstant();
    Instant upperBound = monthEnd.atStartOfDay(task.zoneId()).toInstant();
    if (task.startTime() != null && task.startTime().isAfter(lowerBound)) {
      lowerBound = task.startTime();
    }
    if (task.endTime() != null && task.endTime().isBefore(upperBound)) {
      upperBound = task.endTime();
    }
    if (!lowerBound.isBefore(upperBound)) return List.of();

    return switch (task.trigger().type()) {
      case CRON -> cronOccurrences(task, lowerBound, upperBound);
      case ONE_TIME -> oneTimeOccurrences(task, lowerBound, upperBound);
    };
  }

  private List<ScheduleOccurrenceAt> cronOccurrences(
      UnifiedScheduleTask task,
      Instant lowerBound,
      Instant upperBound) {
    CronExpression cron;
    try {
      // Cron 已在业务 Bridge -> ScheduleDefinition 阶段归一化，首页只投影统一表达式。
      cron = new CronExpression(task.trigger().expression());
      cron.setTimeZone(TimeZone.getTimeZone(task.zoneId()));
    } catch (Exception ignored) {
      return List.of();
    }

    List<ScheduleOccurrenceAt> result = new ArrayList<>();
    Date cursor = Date.from(lowerBound.minusMillis(1));
    while (true) {
      Date next = cron.getNextValidTimeAfter(cursor);
      if (next == null) break;
      Instant nextInstant = next.toInstant();
      if (!nextInstant.isBefore(upperBound)) break;

      LocalDateTime dateTime = LocalDateTime.ofInstant(nextInstant, task.zoneId());
      result.add(new ScheduleOccurrenceAt(dateTime));

      // 首页日历按“某天有哪些调度任务”展示，同一任务一天只投影一次。
      Instant nextDay = dateTime.toLocalDate().plusDays(1).atStartOfDay(task.zoneId()).toInstant();
      if (!nextDay.isBefore(upperBound)) break;
      cursor = Date.from(nextDay.minusMillis(1));
    }
    return result;
  }

  private List<ScheduleOccurrenceAt> oneTimeOccurrences(
      UnifiedScheduleTask task,
      Instant lowerBound,
      Instant upperBound) {
    Instant executeAt = task.trigger().executeAt();
    if (executeAt == null || executeAt.isBefore(lowerBound) || !executeAt.isBefore(upperBound)) {
      return List.of();
    }
    return List.of(new ScheduleOccurrenceAt(LocalDateTime.ofInstant(executeAt, task.zoneId())));
  }

  private String scheduleText(ScheduleTrigger trigger, ZoneId zoneId) {
    if (trigger.type() == TriggerType.CRON) return trigger.expression();
    return "单次 " + LocalDateTime.ofInstant(trigger.executeAt(), zoneId).format(ONE_TIME_FORMATTER);
  }

  private Instant metadataInstant(Map<String, String> metadata, String key) {
    if (metadata == null) return null;
    String value = metadata.get(key);
    if (!hasText(value)) return null;
    try {
      return Instant.parse(value.trim());
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private String fallbackName(String taskType, String taskId) {
    return switch (taskType) {
      case "OFFLINE_SYNC" -> "离线同步任务 #" + taskId;
      case "WORKFLOW" -> "工作流调度 #" + taskId;
      case "DATA_QUALITY" -> "数据质量任务 #" + taskId;
      default -> "调度任务 #" + taskId;
    };
  }

  private String detailPath(String taskType, String taskId) {
    return switch (taskType) {
      case "OFFLINE_SYNC" -> "/sync/batch-link-up/" + taskId + "/detail";
      case "WORKFLOW" -> "/workflow/schedules";
      case "DATA_QUALITY" -> "/data-quality/monitor/" + taskId;
      default -> "/";
    };
  }

  private YearMonth resolveMonth(String value) {
    if (!hasText(value)) return YearMonth.now();
    try {
      return YearMonth.parse(value.trim(), MONTH_FORMATTER);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("month 格式必须为 yyyy-MM", exception);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String defaultText(String value, String fallback) {
    return hasText(value) ? value : fallback;
  }

  public record CalendarResponse(
      String month,
      long totalSchedules,
      List<DaySchedule> days,
      List<ScheduleSummary> overview) {}

  public record DaySchedule(
      String date,
      int count,
      List<ScheduleOccurrence> items) {}

  public record ScheduleOccurrence(
      String taskId,
      String taskType,
      String taskName,
      String time,
      String scheduleText,
      String detailPath) {}

  public record ScheduleSummary(
      String taskId,
      String taskType,
      String taskName,
      String scheduleText,
      String nextRunDate,
      String nextRunTime,
      String detailPath) {}

  private record ScheduleOccurrenceAt(LocalDateTime dateTime) {}

  private record UnifiedScheduleTask(
      String taskId,
      String taskType,
      String taskName,
      ScheduleTrigger trigger,
      ZoneId zoneId,
      Instant startTime,
      Instant endTime,
      String scheduleText,
      String detailPath) {
    String key() {
      return taskType + ":" + taskId;
    }
  }
}
