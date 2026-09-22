package io.yak.ops.business.mdm.api;

import io.yak.ops.business.mdm.domain.source.MdmSourceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Request contracts of the master data identification / source binding (ticket 53). */
public final class MdmSourceApi {

  private MdmSourceApi() {}

  /** 确认来源绑定:实体 + 数据源表 + 角色(MAIN/AUXILIARY)+ 可选字段映射(空=同名回退)。 */
  public record ConfirmRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      @NotNull(message = "数据源不能为空") Long datasourceId,
      @Size(max = 128, message = "源库不能超过 128 个字符") String database,
      @Size(max = 128, message = "源模式不能超过 128 个字符") String schema,
      @NotBlank(message = "源表不能为空") @Size(max = 128, message = "源表不能超过 128 个字符")
          String table,
      String role,
      Map<String, String> fieldMapping) {

    public MdmSourceRole toRole() {
      return role == null || role.isBlank() ? MdmSourceRole.MAIN : MdmSourceRole.valueOf(role);
    }
  }
}
