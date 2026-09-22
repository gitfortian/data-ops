package io.yak.ops.business.metadata.detail;

import io.yak.framework.common.PagingData;
import java.util.Collection;
import java.util.Map;

/**
 * 详情分区的一块状态（ticket 118 的分区容错口径）。
 *
 * <p>三态不可省成两态：把"没接上"静默折成"没有"，运维与用户看到的是同一张空面板，
 * 而修复方向完全相反。{@code UNAVAILABLE} 必带错误码与原因原文。
 *
 * @param status OK=有数据；EMPTY=确实没有；UNAVAILABLE=这块读不了
 * @param code 仅 UNAVAILABLE 有值（49021/49025…）
 * @param message 仅 UNAVAILABLE 有值：哪个块、为什么
 * @param data 块内容；UNAVAILABLE 时为 null
 */
public record SectionState(String status, Integer code, String message, Object data) {

  public static final String OK = "OK";
  public static final String EMPTY = "EMPTY";
  public static final String UNAVAILABLE = "UNAVAILABLE";

  /** 空集合与 null 一律落成 {@link #EMPTY}：调用方不必自己判空，也就不会各判各的。 */
  public static SectionState of(Object data) {
    if (data == null || isEmptyShape(data)) {
      return new SectionState(EMPTY, null, null, data == null ? Map.of() : data);
    }
    return new SectionState(OK, null, null, data);
  }

  public static SectionState unavailable(int code, String reason) {
    return new SectionState(UNAVAILABLE, code, reason, null);
  }

  private static boolean isEmptyShape(Object data) {
    if (data instanceof Collection<?> collection) {
      return collection.isEmpty();
    }
    if (data instanceof Map<?, ?> map) {
      return map.isEmpty();
    }
    // 分页块判的是这一页有没有行：PagingData 对象本身永不为空，漏判会让空时间线报成 OK。
    return data instanceof PagingData<?> page && page.getBizData().isEmpty();
  }
}
