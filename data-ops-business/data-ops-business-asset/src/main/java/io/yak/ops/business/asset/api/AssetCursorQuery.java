package io.yak.ops.business.asset.api;

import java.time.LocalDateTime;

/**
 * 游标批量查询条件。cursor 为上一批 {@link AssetPage#nextCursor()}(不透明,数值主键域用
 * 字符串主键);首页传 null。limit 在构造时钳制到 1~{@value #MAX_LIMIT}(D11 固定查询预算)。
 */
public record AssetCursorQuery(
    Long projectId, LocalDateTime updatedAfter, String cursor, int limit) {

  public static final int MAX_LIMIT = 500;

  public AssetCursorQuery {
    limit = Math.min(Math.max(limit, 1), MAX_LIMIT);
  }
}
