package io.yak.ops.business.quality.schedule;

import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class QualityScheduleCalculator {
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  /** 将数据质量的友好调度配置统一转换成 Yak Schedule/Quartz 使用的 Cron。 */
  public String cronExpression(
      ScheduleFrequency frequency,
      String scheduleTime,
      ScheduleWeekday weekday,
      String cronExpression) {
    if (frequency == null) throw new IllegalArgumentException("调度触发必须选择调度周期");
    return switch (frequency) {
      case DAILY -> dailyCron(parseTime(scheduleTime));
      case WEEKLY -> weeklyCron(parseTime(scheduleTime), requiredWeekday(weekday));
      case CRON -> normalizeQuartzCron(cronExpression);
    };
  }

  public void validateCron(String expression) { parseCron(expression); }

  private String dailyCron(LocalTime time) {
    return "0 " + time.getMinute() + " " + time.getHour() + " * * ?";
  }

  private String weeklyCron(LocalTime time, ScheduleWeekday weekday) {
    return "0 " + time.getMinute() + " " + time.getHour() + " ? * " + weekday.name();
  }

  private String normalizeQuartzCron(String value) {
    validateCron(value);
    String[] fields = value.trim().replaceAll("\\s+", " ").split(" ");
    if (fields.length >= 6) {
      if ("*".equals(fields[3]) && !"?".equals(fields[5])) {
        fields[3] = "?";
      } else if (!"?".equals(fields[3]) && "*".equals(fields[5])) {
        fields[5] = "?";
      }
    }
    return String.join(" ", Arrays.asList(fields));
  }

  private LocalTime parseTime(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("调度触发必须填写执行时间");
    try {
      return LocalTime.parse(value.trim(), TIME_FORMATTER);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("执行时间格式必须为 HH:mm", exception);
    }
  }

  private ScheduleWeekday requiredWeekday(ScheduleWeekday weekday) {
    if (weekday == null) throw new IllegalArgumentException("每周调度必须选择执行日期");
    return weekday;
  }

  private CronExpression parseCron(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Cron 表达式不能为空");
    try {
      return CronExpression.parse(value.trim());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Cron 表达式格式不正确", exception);
    }
  }
}
