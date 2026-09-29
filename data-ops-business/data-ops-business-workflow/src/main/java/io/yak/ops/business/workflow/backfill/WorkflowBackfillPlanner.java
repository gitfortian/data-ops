package io.yak.ops.business.workflow.backfill;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/** 无 Quartz 业务依赖地生成 Workflow 历史补数逻辑计划时间。 */
@Component
public class WorkflowBackfillPlanner {
  public static final int MAX_OCCURRENCES = 1000;
  private static final long MAX_RANGE_DAYS = 3660L;
  private static final int QUARTZ_MIN_YEAR = 1970;
  private static final int QUARTZ_MAX_YEAR = 2199;

  public Plan plan(
      String cronExpression,
      String timezone,
      LocalDate startBusinessDate,
      LocalDate endBusinessDate) {
    if (startBusinessDate == null || endBusinessDate == null) {
      throw new IllegalArgumentException("补数开始和结束业务日期不能为空");
    }
    if (endBusinessDate.isBefore(startBusinessDate)) {
      throw new IllegalArgumentException("补数结束业务日期不能早于开始业务日期");
    }
    long days = ChronoUnit.DAYS.between(startBusinessDate, endBusinessDate) + 1L;
    if (days > MAX_RANGE_DAYS) {
      throw new IllegalArgumentException("单次补数业务日期跨度不能超过 " + MAX_RANGE_DAYS + " 天");
    }

    ZoneId zone = ZoneId.of(required(timezone, "补数时区不能为空"));
    NormalizedCron normalizedCron = normalizeCron(cronExpression);
    ParsedCron cron = parseCron(normalizedCron);
    Instant rangeStart = startBusinessDate.atStartOfDay(zone).toInstant();
    Instant rangeEndExclusive = endBusinessDate.plusDays(1).atStartOfDay(zone).toInstant();

    List<Occurrence> occurrences = new ArrayList<>();
    ZonedDateTime cursor = rangeStart.atZone(zone).minusNanos(1L);
    while (true) {
      ZonedDateTime next = cron.expression().next(cursor);
      if (next == null) break;
      Instant instant = next.toInstant();
      if (!instant.isBefore(rangeEndExclusive)) break;

      if (!cron.yearConstraint().matches(next.getYear())) {
        Integer nextYear = cron.yearConstraint().nextAllowedYear(next.getYear() + 1);
        if (nextYear == null) break;
        ZonedDateTime jump = LocalDate.of(nextYear, 1, 1).atStartOfDay(zone).minusNanos(1L);
        if (!jump.toInstant().isBefore(rangeEndExclusive)) break;
        cursor = jump;
        continue;
      }

      if (!instant.isBefore(rangeStart)) {
        occurrences.add(new Occurrence(
            next.toLocalDate(),
            instant,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(next)));
        if (occurrences.size() > MAX_OCCURRENCES) {
          throw new IllegalArgumentException(
              "单次补数最多生成 " + MAX_OCCURRENCES + " 个计划实例，请缩小日期范围");
        }
      }
      cursor = next;
    }

    if (occurrences.isEmpty()) {
      throw new IllegalArgumentException("当前 Cron 在所选业务日期范围内没有可执行计划时间");
    }
    return new Plan(zone.getId(), normalizedCron.expression(), List.copyOf(occurrences));
  }

  private ParsedCron parseCron(NormalizedCron normalized) {
    String[] fields = normalized.expression().split(" ");
    String[] springFields = Arrays.copyOf(fields, 6);
    if (!normalized.fromFiveFields()) {
      springFields[5] = quartzDayOfWeekToSpring(springFields[5]);
    }

    try {
      CronExpression expression = CronExpression.parse(String.join(" ", springFields));
      YearConstraint years = fields.length == 7
          ? parseYearConstraint(fields[6])
          : YearConstraint.unrestricted();
      return new ParsedCron(expression, years);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "补数无法解析 Cron 表达式：" + normalized.expression(), exception);
    }
  }

  private NormalizedCron normalizeCron(String expression) {
    String cron = required(expression, "Cron 表达式不能为空").replaceAll("\\s+", " ");
    String[] fields = cron.split(" ");
    if (fields.length == 5) {
      String minute = fields[0];
      String hour = fields[1];
      String dayOfMonth = fields[2];
      String month = fields[3];
      String dayOfWeek = fields[4];
      boolean monthDaySpecified = !"*".equals(dayOfMonth) && !"?".equals(dayOfMonth);
      boolean weekDaySpecified = !"*".equals(dayOfWeek) && !"?".equals(dayOfWeek);
      if (monthDaySpecified && weekDaySpecified) {
        throw new IllegalArgumentException(
            "5 字段 Cron 同时指定日和周时与 Quartz 语义不等价，请改为 6/7 字段 Quartz Cron");
      }
      if (weekDaySpecified) dayOfMonth = "?";
      else dayOfWeek = "?";
      return new NormalizedCron(
          String.join(" ", "0", minute, hour, dayOfMonth, month, dayOfWeek),
          true);
    }
    if (fields.length == 6 || fields.length == 7) {
      return new NormalizedCron(cron, false);
    }
    throw new IllegalArgumentException("Backfill 仅支持 5、6 或 7 字段 Cron 表达式");
  }

  /**
   * Spring Cron 使用 0-6 表示 SUN-SAT，而 Quartz 使用 1-7。
   * 这里只转换 6/7 字段 Quartz-style Cron 的星期字段，英文星期名无需转换。
   */
  private String quartzDayOfWeekToSpring(String field) {
    if ("?".equals(field) || "*".equals(field)) return field;
    String[] parts = field.split(",");
    List<String> converted = new ArrayList<>(parts.length);
    for (String part : parts) {
      converted.add(convertQuartzDayOfWeekPart(part));
    }
    return String.join(",", converted);
  }

  private String convertQuartzDayOfWeekPart(String value) {
    String part = value.trim().toUpperCase(Locale.ROOT);
    if (part.isEmpty()) {
      throw new IllegalArgumentException("星期字段不能为空");
    }
    if ("L".equals(part)) {
      // Quartz 在 day-of-week 中单独的 L 等价于 7/SAT。
      return "6";
    }

    int hashIndex = part.indexOf('#');
    if (hashIndex > 0) {
      return convertQuartzDayOfWeekBase(part.substring(0, hashIndex))
          + part.substring(hashIndex);
    }
    if (part.endsWith("L") && part.length() > 1) {
      return convertQuartzDayOfWeekBase(part.substring(0, part.length() - 1)) + "L";
    }

    int slashIndex = part.indexOf('/');
    if (slashIndex > 0) {
      return convertQuartzDayOfWeekBase(part.substring(0, slashIndex))
          + part.substring(slashIndex);
    }
    return convertQuartzDayOfWeekBase(part);
  }

  private String convertQuartzDayOfWeekBase(String value) {
    if ("*".equals(value) || "?".equals(value)) return value;
    int rangeIndex = value.indexOf('-');
    if (rangeIndex > 0) {
      return convertQuartzDayOfWeekValue(value.substring(0, rangeIndex))
          + "-"
          + convertQuartzDayOfWeekValue(value.substring(rangeIndex + 1));
    }
    return convertQuartzDayOfWeekValue(value);
  }

  private String convertQuartzDayOfWeekValue(String value) {
    try {
      int day = Integer.parseInt(value);
      if (day < 1 || day > 7) {
        throw new IllegalArgumentException("Quartz 星期数字必须在 1 到 7 之间：" + value);
      }
      return Integer.toString(day - 1);
    } catch (NumberFormatException ignored) {
      return value;
    }
  }

  private YearConstraint parseYearConstraint(String expression) {
    String field = required(expression, "Cron 年份字段不能为空");
    List<Integer> allowedYears = new ArrayList<>();
    for (int year = QUARTZ_MIN_YEAR; year <= QUARTZ_MAX_YEAR; year++) {
      if (matchesYearExpression(field, year)) {
        allowedYears.add(year);
      }
    }
    if (allowedYears.isEmpty()) {
      throw new IllegalArgumentException("Cron 年份字段没有可执行年份：" + expression);
    }
    return new YearConstraint(false, List.copyOf(allowedYears));
  }

  private boolean matchesYearExpression(String expression, int year) {
    for (String part : expression.split(",")) {
      if (matchesYearPart(part.trim(), year)) return true;
    }
    return false;
  }

  private boolean matchesYearPart(String part, int year) {
    if (part.isEmpty()) {
      throw new IllegalArgumentException("Cron 年份字段格式不正确");
    }
    String[] stepParts = part.split("/", -1);
    if (stepParts.length > 2 || stepParts[0].isEmpty()) {
      throw new IllegalArgumentException("Cron 年份字段格式不正确：" + part);
    }
    int step = stepParts.length == 2 ? positiveInt(stepParts[1], "Cron 年份步长") : 1;

    int start;
    int end;
    String base = stepParts[0];
    if ("*".equals(base)) {
      start = QUARTZ_MIN_YEAR;
      end = QUARTZ_MAX_YEAR;
    } else {
      int rangeIndex = base.indexOf('-');
      if (rangeIndex > 0) {
        start = quartzYear(base.substring(0, rangeIndex));
        end = quartzYear(base.substring(rangeIndex + 1));
      } else {
        start = quartzYear(base);
        end = stepParts.length == 2 ? QUARTZ_MAX_YEAR : start;
      }
    }
    if (end < start) {
      throw new IllegalArgumentException("Cron 年份范围不正确：" + part);
    }
    return year >= start && year <= end && (year - start) % step == 0;
  }

  private int quartzYear(String value) {
    int year = positiveInt(value, "Cron 年份");
    if (year < QUARTZ_MIN_YEAR || year > QUARTZ_MAX_YEAR) {
      throw new IllegalArgumentException(
          "Cron 年份必须在 " + QUARTZ_MIN_YEAR + " 到 " + QUARTZ_MAX_YEAR + " 之间：" + value);
    }
    return year;
  }

  private int positiveInt(String value, String label) {
    try {
      int parsed = Integer.parseInt(value);
      if (parsed <= 0) throw new NumberFormatException(value);
      return parsed;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(label + "必须是正整数：" + value, exception);
    }
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }

  private record NormalizedCron(String expression, boolean fromFiveFields) {
  }

  private record ParsedCron(CronExpression expression, YearConstraint yearConstraint) {
  }

  private record YearConstraint(boolean any, List<Integer> allowedYears) {
    static YearConstraint unrestricted() {
      return new YearConstraint(true, List.of());
    }

    boolean matches(int year) {
      return any || allowedYears.contains(year);
    }

    Integer nextAllowedYear(int lowerBound) {
      if (any) return lowerBound;
      for (Integer year : allowedYears) {
        if (year >= lowerBound) return year;
      }
      return null;
    }
  }

  public record Plan(
      String timezone,
      String cronExpression,
      List<Occurrence> occurrences) {
  }

  public record Occurrence(
      LocalDate businessDate,
      Instant scheduleInstant,
      String scheduleTime) {
  }
}
