package io.yak.ops.common.bean.dto.quality;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 数据质量已注册表请求契约。 */
public final class QualityTableAssetDTO {
  private QualityTableAssetDTO() {}

  public record PageRequest(
      @Min(1) Integer current,
      @Min(1) @Max(100) Integer pageSize,
      @NotNull Long dataSourceId,
      String databaseName,
      String schemaName,
      String keyword) {
    public int normalizedCurrent() { return current == null ? 1 : current; }
    public int normalizedPageSize() { return pageSize == null ? 20 : pageSize; }
  }

  public record RegisterItem(
      @Size(max = 128) String databaseName,
      @Size(max = 128) String schemaName,
      @NotBlank @Size(max = 256) String tableName,
      @Size(max = 40) String tableType,
      @Size(max = 1000) String remarks) {}

  public record RegisterRequest(
      @NotNull Long dataSourceId,
      @NotBlank @Size(max = 128) String dataSourceName,
      @Size(max = 128) String databaseName,
      @NotEmpty List<@Valid RegisterItem> tables) {}
}
