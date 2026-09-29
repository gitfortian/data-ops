package io.yak.ops.business.modeling.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request contracts of reverse import (ticket 08). */
public final class ModelingImportApi {

  private ModelingImportApi() {}

  /** 浏览/搜索数据源表。 */
  public record TableBrowseRequest(
      @NotNull(message = "数据源不能为空") Long datasourceId,
      @Size(max = 128, message = "关键字不能超过 128 个字符") String keyword) {}

  /** 预览一张表的列结构。 */
  public record PreviewRequest(
      @NotNull(message = "数据源不能为空") Long datasourceId,
      @NotBlank(message = "库不能为空") String database,
      @NotBlank(message = "表不能为空") String table) {}

  /** 单表导入项:code 可改(默认=表名);eventTimeField 指定 event_time 的来源业务字段(可空=自动识别)。 */
  public record ImportItem(
      @NotNull(message = "数据源不能为空") Long datasourceId,
      @NotBlank(message = "库不能为空") String database,
      @NotBlank(message = "表不能为空") String table,
      String code,
      String name,
      String remarks,
      String eventTimeField) {}

  /** 批量导入请求:方言必填;目录可空;layerCode 缺省 ODS(38 分层定位)。 */
  public record ImportRequest(
      @NotBlank(message = "目标方言不能为空") String dialect,
      Long directoryId,
      String layerCode,
      @jakarta.validation.Valid @NotNull(message = "导入表不能为空") List<ImportItem> tables) {}

  /** 导入结果报告。 */
  public record ImportResult(
      List<String> created,
      List<String> filled,
      List<String> skipped,
      List<FailedImport> failed,
      List<ImportedModel> models,
      StandardApplyStats standardApply) {

    public record FailedImport(String table, String reason) {}

    /**
     * 单表去向:供前端"打开模型"直达详情;失败发生在建模型之前时 modelId 为空。
     * fields 为逐列治理明细(38 评审补充:命中/未命中都要可见)。
     */
    public record ImportedModel(
        String table,
        Long modelId,
        ImportAction action,
        List<ColumnMatch> fields) {}

    /** 建模型 / 已有空模型补字段 / 已有字段跳过 / 失败。 */
    public enum ImportAction {
      CREATED,
      FILLED,
      SKIPPED,
      FAILED
    }

    /**
     * 逐列匹配明细:命中标准字段(35)时给 id/名称与匹配方式;未命中为空并标 warning;
     * 类型/命名/安全标准降级原因一并回报(degradedReason 为空即无降级)。
     * 名称相似(模糊)只给建议(suggested*)不自动关联,避免把"订单号"错关联成"订单ID"。
     * 实际套用的类型标准以 stdTypeId/stdTypeName 透出(53:匹配失败为 null,不再默认套"标识")。
     */
    public record ColumnMatch(
        String columnName,
        String dataType,
        Long stdFieldId,
        String stdFieldCode,
        String stdFieldName,
        /** 关联或建议的判定方式:exact/comment(已关联)、fuzzy(仅建议)。 */
        String matchedBy,
        String degradedReason,
        boolean technical,
        Long suggestedStdFieldId,
        String suggestedStdFieldName,
        /** 实际套用的类型标准(53;null = 未套用,交用户处理)。 */
        Long stdTypeId,
        String stdTypeName) {}

    /** 标准自动套用统计(38;降级数 = 无候选 + 推荐失败)。 */
    public record StandardApplyStats(
        int typeApplied, int securityApplied, int namingApplied, int degraded) {}
  }
}
