package io.yak.ops.business.mdm.api;

import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request contracts of the master data attribute (ticket 52). */
public final class MdmAttributeApi {

  private MdmAttributeApi() {}

  /** 创建属性:编码实体内唯一,创建后不可改。 */
  public record CreateRequest(
      @NotBlank(message = "属性编码不能为空")
          @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "属性编码仅允许字母、数字和下划线,1~64 位")
          String code,
      @NotBlank(message = "属性名称不能为空") @Size(max = 128, message = "属性名称不能超过 128 个字符")
          String name,
      @NotBlank(message = "属性角色不能为空") String attrType,
      @Size(max = 64, message = "数据类型不能超过 64 个字符") String dataType,
      Long stdTypeId,
      Long stdUnitId,
      @Size(max = 64, message = "码集编码不能超过 64 个字符") String stdCodeSetCode,
      Long stdSecurityId,
      Boolean required,
      @Size(max = 512, message = "业务描述不能超过 512 个字符") String businessDesc,
      Integer sortOrder) {

    public MdmAttributeType toType() {
      return MdmAttributeType.valueOf(attrType);
    }
  }

  /** 编辑属性:编码不可改。 */
  public record UpdateRequest(
      @NotBlank(message = "属性名称不能为空") @Size(max = 128, message = "属性名称不能超过 128 个字符")
          String name,
      @NotBlank(message = "属性角色不能为空") String attrType,
      @Size(max = 64, message = "数据类型不能超过 64 个字符") String dataType,
      Long stdTypeId,
      Long stdUnitId,
      @Size(max = 64, message = "码集编码不能超过 64 个字符") String stdCodeSetCode,
      Long stdSecurityId,
      Boolean required,
      @Size(max = 512, message = "业务描述不能超过 512 个字符") String businessDesc,
      Integer sortOrder) {

    public MdmAttributeType toType() {
      return MdmAttributeType.valueOf(attrType);
    }
  }
}
