package io.yak.ops.business.sync.realtime.engine;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Defense-in-depth redaction for the bounded Gateway log proxy. */
@Component
public class RealtimeLogRedactor {

  private static final Pattern KEY_VALUE_SECRET =
      Pattern.compile("(?i)((?:password|pwd|token|secret)\\s*[=:]\\s*)[^\\s,;]+");
  private static final Pattern URL_PASSWORD = Pattern.compile("(?i)(://[^:/?#\\s]+:)[^@/?#\\s]+@");

  public String redact(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    String masked = KEY_VALUE_SECRET.matcher(value).replaceAll("$1******");
    return URL_PASSWORD.matcher(masked).replaceAll("$1******@");
  }
}
