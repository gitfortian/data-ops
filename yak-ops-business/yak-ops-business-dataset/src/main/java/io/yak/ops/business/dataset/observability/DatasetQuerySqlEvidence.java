package io.yak.ops.business.dataset.observability;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Builds privacy-safe SQL evidence for diagnostics without persisting literal query values. */
@Component
public class DatasetQuerySqlEvidence {

  static final int MAX_SQL_PREVIEW_LENGTH = 4000;
  static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  private static final Pattern BLOCK_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
  private static final Pattern LINE_COMMENT = Pattern.compile("(?m)--[^\\r\\n]*");
  private static final Pattern STRING_LITERAL = Pattern.compile("'(?:''|[^'])*'");
  private static final Pattern HEX_LITERAL = Pattern.compile("(?i)\\b0x[0-9a-f]+\\b");
  private static final Pattern NUMBER_LITERAL = Pattern.compile(
      "(?<![A-Za-z0-9_])[-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?(?![A-Za-z0-9_])");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  public DatasetQueryPerformance sanitize(DatasetQueryPerformance trace) {
    if (trace == null) return null;
    Evidence evidence = evidence(trace.sql());
    return new DatasetQueryPerformance(
        trace.queryId(),
        trace.datasetId(),
        trace.datasetName(),
        trace.datasetVersionId(),
        trace.datasetVersionNo(),
        trace.sourceType(),
        trace.dataSourceId(),
        evidence.preview(),
        evidence.hash(),
        trace.status(),
        trace.failureStage(),
        trace.errorType(),
        safeText(trace.errorMessage(), MAX_ERROR_MESSAGE_LENGTH),
        trace.waitMillis(),
        trace.prepareMillis(),
        trace.executeMillis(),
        trace.transferMillis(),
        trace.totalMillis(),
        trace.returnedRows(),
        trace.truncated(),
        trace.startedAt(),
        trace.finishedAt());
  }

  Evidence evidence(String sql) {
    if (sql == null || sql.isBlank()) return new Evidence(null, null);
    String normalized = redact(sql);
    if (normalized.isBlank()) return new Evidence(null, null);
    return new Evidence(truncate(normalized, MAX_SQL_PREVIEW_LENGTH), sha256(normalized));
  }

  private String safeText(String value, int maxLength) {
    if (value == null || value.isBlank()) return null;
    return truncate(redact(value), maxLength);
  }

  private String redact(String value) {
    String result = BLOCK_COMMENT.matcher(value).replaceAll(" ");
    result = LINE_COMMENT.matcher(result).replaceAll(" ");
    result = STRING_LITERAL.matcher(result).replaceAll("'?'");
    result = HEX_LITERAL.matcher(result).replaceAll("?");
    result = NUMBER_LITERAL.matcher(result).replaceAll("?");
    return WHITESPACE.matcher(result).replaceAll(" ").trim();
  }

  private String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JVM 不支持 SHA-256", exception);
    }
  }

  record Evidence(String preview, String hash) {}
}
