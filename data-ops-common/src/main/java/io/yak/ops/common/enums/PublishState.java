package io.yak.ops.common.enums;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可版本化业务对象的统一发布态（契约 C1）。
 *
 * <p>与 ENABLED/DISABLED 类"可用性开关"正交：发布态回答"哪个版本生效"，开关回答"对象整体是否可用"。
 * 存量别名在解析层收敛（如 workflow 历史值 ONLINE → PUBLISHED），持久化一律写本枚举名。
 */
public enum PublishState {
  DRAFT,
  PUBLISHED,
  OFFLINE;

  private static final Logger log = LoggerFactory.getLogger(PublishState.class);

  /** 宽松解析：兼容存量字面量；无法识别时回退 DRAFT 并告警，不抛异常。 */
  public static PublishState of(String raw) {
    if (raw == null || raw.isBlank()) {
      return DRAFT;
    }
    return switch (raw.trim().toUpperCase()) {
      case "DRAFT" -> DRAFT;
      case "PUBLISHED", "ONLINE", "ACTIVE" -> PUBLISHED;
      case "OFFLINE", "OFF", "DISABLED" -> OFFLINE;
      default -> {
        log.warn("Unrecognized publish state '{}', falling back to DRAFT", raw);
        yield DRAFT;
      }
    };
  }

  public boolean matches(String raw) {
    return of(raw) == this;
  }
}
