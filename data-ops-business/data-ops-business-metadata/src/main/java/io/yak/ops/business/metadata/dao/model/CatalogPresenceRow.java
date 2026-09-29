package io.yak.ops.business.metadata.dao.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 在场性判定所需的一行<b>现状</b>：谁还挂着、最后一次被采到是什么时候。
 *
 * <p>只带八列，且不含 {@code md_attributes}：在场性判断问的是"这行还在不在源侧"，
 * 把属性袋一起读回来会让一轮采集把整库的治理信息搬进内存。
 *
 * <p>{@code lastCollectAt} 是判"连续两轮未见"的唯一依据（ticket 115）。它够用是因为
 * {@code last_collect_at} 每轮必刷且只有这里读它——上一轮没刷到，就说明上一轮也没看见这个实体。
 */
@Data
public class CatalogPresenceRow {

  private Long id;
  private String assetKey;
  /** lineage 的 {@code LineageAssetType} 字面量：{@code DATABASE}/{@code TABLE}/{@code COLUMN}。 */
  private String assetType;
  private String databaseName;
  private String schemaName;
  private String tableName;
  /** 行自己的结构指纹；GONE 的变更流水要留"消失前是哪份结构"。 */
  private String contentHash;
  /** 登记行的"消失前指纹"是它而非 contentHash（REGISTERED 行的 content_hash 恒为 NULL，ticket 130）。 */
  private String sourceHash;
  private LocalDateTime lastCollectAt;
}
