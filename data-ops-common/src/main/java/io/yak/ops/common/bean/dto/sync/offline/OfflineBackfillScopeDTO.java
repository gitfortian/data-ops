package io.yak.ops.common.bean.dto.sync.offline;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** One data range inside an offline backfill request. */
@Data
public class OfflineBackfillScopeDTO {

  @NotBlank(message = "scope.type 不能为空")
  private String type;

  private LocalDateTime startInclusive;
  private LocalDateTime endExclusive;
  private List<String> partitions;
  private String cursorId;
  private String cursorColumn;
  private String afterExclusive;
  private String throughInclusive;
}
