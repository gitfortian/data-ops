package io.yak.ops.common.bean.vo.quality;

import io.yak.ops.common.annotation.quality.QualityDateTimeFormat;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import java.time.LocalDateTime;
import java.util.List;

/** 数据质量已注册表响应契约。 */
public final class QualityTableAssetVO {
  private QualityTableAssetVO() {}

  public record Asset(
      Long id,
      Long dataSourceId,
      String dataSourceName,
      String databaseName,
      String schemaName,
      String tableName,
      String tableType,
      String remarks,
      Long monitorId,
      String monitorName,
      int monitorCount,
      int ruleCount,
      CheckResult lastResult,
      @QualityDateTimeFormat LocalDateTime lastRunTime,
      String registeredBy,
      @QualityDateTimeFormat LocalDateTime registeredAt) {}

  public record Page(List<Asset> records, long total, int current, int pageSize) {}

  public record Candidate(
      String databaseName,
      String schemaName,
      String tableName,
      String tableType,
      String remarks) {}

  public record CandidatePage(List<Candidate> records, long total, int current, int pageSize) {}
  public record RegisterResult(int requested, int registered) {}
}
