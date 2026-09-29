package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.derive.ModelDeriveService;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Process-driven model derivation (ticket 44). */
@Tag(name = "数据建模派生建模接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/derive")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingDeriveController {

  private final ModelDeriveService deriveService;
  private final CurrentUserProvider currentUserProvider;

  /** 派生请求 DTO。 */
  @Data
  public static class DeriveRequestBody {
    @NotNull(message = "业务过程不能为空")
    Long processId;

    @NotBlank(message = "目标分层不能为空")
    @Size(max = 32, message = "分层编码不能超过 32 个字符")
    String layerCode;

    @NotBlank(message = "模型编码不能为空")
    @Size(max = 128, message = "模型编码不能超过 128 个字符")
    String code;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 128, message = "模型名称不能超过 128 个字符")
    String name;

    @NotBlank(message = "目标方言不能为空")
    String dialect;

    @Size(max = 512, message = "描述不能超过 512 个字符")
    String description;

    Long directoryId;

    /** 维表 SCD 类型(50):SCD1 默认/SCD2;仅 DIM 目标生效。 */
    @Size(max = 16, message = "SCD 类型不能超过 16 个字符")
    String scdType;

    /** 51/52:参与的上游模型 ID(缺省=该过程该上游层的全部模型)。 */
    List<Long> upstreamModelIds;

    /** 51:统计周期(1h/1d/1w/1m/ALL;仅聚合层生效)。 */
    @Size(max = 8, message = "统计周期不能超过 8 个字符")
    String statPeriod;

    /** 52:应用/报表绑定。 */
    @Size(max = 64, message = "应用编码不能超过 64 个字符")
    String appCode;

    @Size(max = 128, message = "应用名称不能超过 128 个字符")
    String appName;

    /** 61:数据来源覆盖(DWS 默认/DWD 明细实时报表;仅 ADS 生效)。 */
    @Size(max = 16, message = "数据来源不能超过 16 个字符")
    String upstreamLayer;

    @Valid
    List<SelectedFieldBody> fields;
  }

  @Data
  public static class SelectedFieldBody {
    /** 继承来源:源表名(目标层技术列可为空)。 */
    @Size(max = 128, message = "源表名不能超过 128 个字符")
    String sourceTable;

    @NotBlank(message = "源字段不能为空")
    @Size(max = 128, message = "源字段不能超过 128 个字符")
    String sourceColumn;

    @Size(max = 128, message = "落地字段名不能超过 128 个字符")
    String landingField;

    /** 标准字段关联(用户关联/沉淀后的结果;空=未治理)。 */
    Long stdFieldId;

    @Size(max = 1024, message = "转换表达式不能超过 1024 个字符")
    String transformExpr;

    Boolean include;

    /** 目标层技术列(按分层规则补充)。 */
    Boolean technical;

    /** 聚合层字段角色(51/52):DIMENSION 分组键 / MEASURE 度量。 */
    @Size(max = 16, message = "字段角色不能超过 16 个字符")
    String fieldRole;

    /** 聚合函数(MEASURE 必填:SUM/COUNT/COUNT_DISTINCT/MAX/MIN/AVG)。 */
    @Size(max = 16, message = "聚合函数不能超过 16 个字符")
    String aggregateFunc;
  }

  @Operation(summary = "派生预览：上游就绪情况 + 字段继承/聚合清单 + 治理率（只读）")
  @GetMapping("/preview")
  public Result<ModelDeriveService.PreviewView> preview(
      @RequestParam("processId") Long processId,
      @RequestParam("layerCode") String layerCode,
      @RequestParam(value = "dialect", required = false) String dialect,
      @RequestParam(value = "scdType", required = false) String scdType,
      @RequestParam(value = "upstreamModelIds", required = false) List<Long> upstreamModelIds,
      @RequestParam(value = "statPeriod", required = false) String statPeriod,
      @RequestParam(value = "appCode", required = false) String appCode,
      @RequestParam(value = "appName", required = false) String appName,
      @RequestParam(value = "upstreamLayer", required = false) String upstreamLayer) {
    return Result.success(
        deriveService.preview(
            processId, layerCode, dialect, scdType, upstreamModelIds, statPeriod, appCode,
            appName, upstreamLayer));
  }

  @Operation(summary = "指标反推草稿(60/61):选指标→上游模型 + 统计周期 + 度量/维度建议")
  @GetMapping("/metric-draft")
  public Result<ModelDeriveService.MetricDraftView> metricDraft(
      @RequestParam("metricIds") List<Long> metricIds,
      @RequestParam(value = "dialect", required = false) String dialect,
      @RequestParam(value = "sourceLayer", required = false) String sourceLayer) {
    return Result.success(deriveService.metricDraft(metricIds, dialect, sourceLayer));
  }

  @Operation(summary = "按业务过程派生模型草稿（自动写映射并登记血缘）")
  @RequiresPermission(ModelingPermissionCode.CREATE)
  @PostMapping
  public Result<ModelDeriveService.DeriveView> derive(
      @Valid @RequestBody DeriveRequestBody request, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    ModelDeriveService.DeriveView view =
        deriveService.derive(toDomain(request), operator);
    return Result.success(view);
  }

  private static ModelDeriveService.DeriveRequest toDomain(DeriveRequestBody body) {
    List<ModelDeriveService.DerivedField> fields =
        body.getFields() == null
            ? null
            : body.getFields().stream()
                .map(
                    item ->
                        new ModelDeriveService.DerivedField(
                            item.getSourceTable(),
                            item.getSourceColumn(),
                            item.getLandingField(),
                            item.getStdFieldId(),
                            item.getTransformExpr(),
                            !Boolean.FALSE.equals(item.getInclude()),
                            Boolean.TRUE.equals(item.getTechnical()),
                            item.getFieldRole(),
                            item.getAggregateFunc()))
                .toList();
    return new ModelDeriveService.DeriveRequest(
        body.getProcessId(),
        body.getLayerCode(),
        body.getCode(),
        body.getName(),
        body.getDialect(),
        body.getDescription(),
        body.getDirectoryId(),
        body.getScdType(),
        body.getUpstreamModelIds(),
        body.getStatPeriod(),
        body.getAppCode(),
        body.getAppName(),
        body.getUpstreamLayer(),
        fields);
  }
}
