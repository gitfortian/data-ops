package io.yak.ops.common.bean.dto.sync.offline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/** Offline backfill request: one request materializes a group of BatchExecution. */
@Data
public class OfflineBackfillRequestDTO {

  @NotBlank(message = "requestId 不能为空")
  private String requestId;

  @Valid
  @NotEmpty(message = "scopes 不能为空")
  private List<OfflineBackfillScopeDTO> scopes;
}
