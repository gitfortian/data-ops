package io.yak.ops.common.bean.vo.sync.offline;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** Result of materializing one backfill request into a Batch group. */
@Data
@Builder
public class OfflineBackfillVO {
  private Long jobDefinitionId;
  private String requestId;
  private List<Long> batchIds;
  private Integer createdCount;
  private Integer reusedCount;
}
