package io.yak.ops.common.bean.dto.sync.offline;

import lombok.Data;

/** Optional, explicit Modeling provenance of one offline task definition. */
@Data
public class OfflineOdsHandoffDTO {
  private Long modelId;
  private Integer versionNo;
  private Long targetObservationId;
}
