package io.yak.ops.business.semantic.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request/response contracts of the semantic standard catalog (ticket 30). */
public final class SemanticStandardApi {

  /** 编码规则:字母/数字/下划线,1~64 位;创建后不可改(DOMAIN.md)。 */
  public static final String CODE_PATTERN = "^[A-Za-z0-9_]{1,64}$";

  private SemanticStandardApi() {}

  /** 创建标准请求:kind + 编码/名称 + 类别专有字段(按 kind 必填,服务层校验)。 */
  public record CreateRequest(
      @NotBlank(message = "标准类别不能为空") String kind,
      @NotBlank(message = "标准编码不能为空")
          @Pattern(regexp = CODE_PATTERN, message = "标准编码仅允许字母、数字和下划线,1~64 位")
          String code,
      @NotBlank(message = "标准名称不能为空") @Size(max = 128, message = "标准名称不能超过 128 个字符")
          String name,
      @Size(max = 512, message = "描述不能超过 512 个字符") String description,
      Integer sortOrder,
      @Size(max = 16, message = "适用范围不能超过 16 个字符") String scope,
      @Size(max = 64, message = "适用分层不能超过 64 个字符") String layer,
      @Size(max = 1024, message = "规则表达式不能超过 1024 个字符") String ruleExpr,
      @Size(max = 256, message = "示例不能超过 256 个字符") String example,
      @Size(max = 64, message = "类型编码不能超过 64 个字符") String typeCode,
      @Size(max = 64, message = "标准类型不能超过 64 个字符") String stdType,
      @Size(max = 8192, message = "源库类型映射不能超过 8192 个字符") String sourceMapping,
      @Size(max = 64, message = "码集编码不能超过 64 个字符") String codeSetCode,
      @Size(max = 256, message = "码值不能超过 256 个字符") String codeValue,
      @Size(max = 256, message = "码值标签不能超过 256 个字符") String codeLabel,
      @Size(max = 64, message = "单位编码不能超过 64 个字符") String unitCode,
      @Size(max = 64, message = "单位类型不能超过 64 个字符") String unitType,
      @Size(max = 64, message = "口径编码不能超过 64 个字符") String caliberCode,
      @Size(max = 1024, message = "口径规则不能超过 1024 个字符") String calRule,
      @Size(max = 512, message = "业务说明不能超过 512 个字符") String businessDesc,
      @Size(max = 64, message = "等级编码不能超过 64 个字符") String levelCode,
      @Size(max = 512, message = "脱敏规则不能超过 512 个字符") String maskRule) {}

  /** 更新标准请求:编码/类别不可改,其余可编辑;每次修改 version 自增。 */
  public record UpdateRequest(
      @NotNull(message = "标准版本不能为空") Integer version,
      @NotBlank(message = "标准名称不能为空") @Size(max = 128, message = "标准名称不能超过 128 个字符")
          String name,
      @Size(max = 512, message = "描述不能超过 512 个字符") String description,
      Integer sortOrder,
      @Size(max = 16, message = "适用范围不能超过 16 个字符") String scope,
      @Size(max = 64, message = "适用分层不能超过 64 个字符") String layer,
      @Size(max = 1024, message = "规则表达式不能超过 1024 个字符") String ruleExpr,
      @Size(max = 256, message = "示例不能超过 256 个字符") String example,
      @Size(max = 64, message = "类型编码不能超过 64 个字符") String typeCode,
      @Size(max = 64, message = "标准类型不能超过 64 个字符") String stdType,
      @Size(max = 8192, message = "源库类型映射不能超过 8192 个字符") String sourceMapping,
      @Size(max = 64, message = "码集编码不能超过 64 个字符") String codeSetCode,
      @Size(max = 256, message = "码值不能超过 256 个字符") String codeValue,
      @Size(max = 256, message = "码值标签不能超过 256 个字符") String codeLabel,
      @Size(max = 64, message = "单位编码不能超过 64 个字符") String unitCode,
      @Size(max = 64, message = "单位类型不能超过 64 个字符") String unitType,
      @Size(max = 64, message = "口径编码不能超过 64 个字符") String caliberCode,
      @Size(max = 1024, message = "口径规则不能超过 1024 个字符") String calRule,
      @Size(max = 512, message = "业务说明不能超过 512 个字符") String businessDesc,
      @Size(max = 64, message = "等级编码不能超过 64 个字符") String levelCode,
      @Size(max = 512, message = "脱敏规则不能超过 512 个字符") String maskRule) {}

  /** 启用/停用请求。 */
  public record StatusRequest(@NotBlank(message = "状态不能为空") String status) {}

  /**
   * 码集批量保存请求:一次写入同一码集下的所有码值(32.1)。
   * originCodeSetCode 仅存量空码集行"补全码集编码"时传:按原组键(std_code)定位行,
   * 将其采纳到新的 codeSetCode 下;正常编辑不传(此时码集编码不可改)。
   */
  public record CodeSetSaveRequest(
      @NotBlank(message = "码集编码不能为空")
          @Size(max = 64, message = "码集编码不能超过 64 个字符")
          String codeSetCode,
      @Size(max = 64, message = "原组键不能超过 64 个字符") String originCodeSetCode,
      @NotBlank(message = "码集名称不能为空")
          @Size(max = 128, message = "码集名称不能超过 128 个字符")
          String name,
      @Size(max = 512, message = "描述不能超过 512 个字符") String description,
      @NotEmpty(message = "码值列表不能为空") @Valid List<CodeValueItem> values,
      String revision) {
    public CodeSetSaveRequest(String codeSetCode, String originCodeSetCode, String name,
        String description, List<CodeValueItem> values) {
      this(codeSetCode, originCodeSetCode, name, description, values, null);
    }
  }

  /** 码集内单个码值条目。 */
  public record CodeValueItem(
      @NotBlank(message = "码值不能为空")
          @Size(max = 256, message = "码值不能超过 256 个字符")
          String codeValue,
      @Size(max = 256, message = "码值标签不能超过 256 个字符") String codeLabel,
      Integer sortOrder) {}
}
