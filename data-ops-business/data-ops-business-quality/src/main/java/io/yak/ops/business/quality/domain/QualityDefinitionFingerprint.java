package io.yak.ops.business.quality.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Editable definition only: runtime results and next-run projections do not invalidate edits. */
public final class QualityDefinitionFingerprint {
  private QualityDefinitionFingerprint() {}

  public static String of(QualityDomain.Monitor m, QualityDomain.MonitorSettings s) {
    try {
      MessageDigest hash = MessageDigest.getInstance("SHA-256");
      add(hash, m.id(), m.name(), m.description(), m.dataSourceId(), m.dataSourceName(),
          m.databaseName(), m.schemaName(), m.tableName(), m.whereClause(), m.owner(), m.enabled());
      add(hash, m.rules() == null ? 0 : m.rules().size());
      if (m.rules() != null) for (var r : m.rules()) {
        add(hash, r.templateId(), r.name(), r.columnName(), r.operator(), r.threshold(),
            r.thresholdEnd(), r.customSql(), r.enabled(), r.sortOrder());
        add(hash, r.enumValues() == null ? 0 : r.enumValues().size());
        if (r.enumValues() != null) r.enumValues().forEach(v -> add(hash, v));
      }
      add(hash, s == null);
      if (s != null) add(hash, s.runMode(), s.scheduleFrequency(), s.scheduleTime(),
          s.scheduleWeekday(), s.cronExpression(), s.ruleFailureAction(), s.notifyEnabled(),
          s.notifyChannel(), s.notifyTarget(), s.alertLevel());
      return HexFormat.of().formatHex(hash.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  private static void add(MessageDigest hash, Object... values) {
    for (Object value : values) {
      byte[] bytes = value == null ? null : value.toString().getBytes(StandardCharsets.UTF_8);
      hash.update(ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array());
      if (bytes != null) hash.update(bytes);
    }
  }
}
