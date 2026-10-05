package io.yak.ops.business.asset.controller.v1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 数据资产模块请求 DTO 集合。 */
public final class AssetRequests {

  private AssetRequests() {}

  @Data
  public static class ItemQueryDTO {
    @Min(value = 1, message = "页码必须大于 0")
    private int pageNo = 1;

    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = 200, message = "每页条数不能超过 200")
    private int pageSize = 20;

    @Size(max = 128, message = "搜索关键词不能超过 128 个字符")
    private String keyword;

    @Size(max = 16, message = "状态不能超过 16 个字符")
    private String status;

    @Size(max = 16, message = "资产类型不能超过 16 个字符")
    private String assetType;

    @Size(max = 16, message = "来源域不能超过 16 个字符")
    private String sourceType;

    @Size(max = 64, message = "负责人不能超过 64 个字符")
    private String owner;

    private Long directoryId;

    /** 多选筛选(ticket 97):与单值字段同时给出时取交集。 */
    private List<String> assetTypes;

    private List<String> layerCodes;

    private List<String> statuses;

    /** 健康等级 A/B/C/D 多选。 */
    private List<String> grades;

    /** 标签 ID 多选,经 yak_asset_tag_rel 反查。 */
    private List<Long> tagIds;

    /** 排序:空=健康×活跃度加权公式;TIME/VIEWS/NAME。 */
    @Size(max = 16, message = "排序字段不能超过 16 个字符")
    private String sortBy;
  }

  /** 手工登记(仅 source_type=MANUAL)。assetCode 空则自动生成。 */
  @Data
  public static class ManualRegisterDTO {
    @NotBlank(message = "资产名称不能为空")
    @Size(max = 128, message = "资产名称不能超过 128 个字符")
    private String name;

    @Size(max = 64, message = "资产编码不能超过 64 个字符")
    private String assetCode;

    @Size(max = 16, message = "资产类型不能超过 16 个字符")
    private String assetType;

    @Size(max = 1024, message = "描述不能超过 1024 个字符")
    private String description;

    @Size(max = 64, message = "负责人不能超过 64 个字符")
    private String owner;

    @Size(max = 512, message = "访问入口不能超过 512 个字符")
    private String accessUri;

    @Size(max = 32, message = "分层编码不能超过 32 个字符")
    private String layerCode;

    @Size(max = 32, message = "业务域不能超过 32 个字符")
    private String domainCode;

    private Long directoryId;
  }

  /** 编辑台账快照字段。 */
  @Data
  public static class ItemEditDTO {
    @jakarta.validation.constraints.Pattern(regexp = "[a-f0-9]{64}")
    private String expectedDefinition;

    @Size(max = 128, message = "资产名称不能超过 128 个字符")
    private String name;

    @Size(max = 1024, message = "描述不能超过 1024 个字符")
    private String description;

    @Size(max = 512, message = "访问入口不能超过 512 个字符")
    private String accessUri;
  }

  @Data
  public static class OwnerDTO {
    @NotBlank(message = "负责人不能为空")
    @Size(max = 64, message = "负责人不能超过 64 个字符")
    private String owner;
  }

  /** 新建/编辑目录:dirCode 空则自动生成 {父编码}_{序号};parentId 空=根。 */
  @Data
  public static class DirectoryUpsertDTO {
    @Size(max = 64, message = "目录编码不能超过 64 个字符")
    private String dirCode;

    @NotBlank(message = "目录名称不能为空")
    @Size(max = 128, message = "目录名称不能超过 128 个字符")
    private String dirName;

    private Long parentId;

    @Size(max = 64, message = "图标不能超过 64 个字符")
    private String iconKey;

    @Size(max = 512, message = "描述不能超过 512 个字符")
    private String description;

    private Integer sortOrder;
  }

  @Data
  public static class DirectoryMoveDTO {
    /** 空=移到根。 */
    private Long targetParentId;
  }

  @Data
  public static class BatchMoveAssetsDTO {
    @NotEmpty(message = "请至少选择一个资产")
    @Size(max = 200, message = "单次最多移动 200 个资产")
    private List<Long> assetIds;

    /** 空=移出目录(未归类)。 */
    private Long directoryId;
  }

  /** 新建/编辑标签:tagCode 空自动生成 tag_{ts}。 */
  @Data
  public static class TagUpsertDTO {
    @Size(max = 64, message = "标签编码不能超过 64 个字符")
    private String tagCode;

    @NotBlank(message = "标签名称不能为空")
    @Size(max = 128, message = "标签名称不能超过 128 个字符")
    private String tagName;

    @Size(max = 32, message = "颜色不能超过 32 个字符")
    private String color;

    @Size(max = 512, message = "描述不能超过 512 个字符")
    private String description;
  }

  @Data
  public static class AddTagsDTO {
    @NotEmpty(message = "请至少选择一个标签")
    @Size(max = 50, message = "单次最多打 50 个标签")
    private List<Long> tagIds;
  }

  /** 新建/编辑编目规则;编辑后一律回到未启用+未试跑(D10)。 */
  @Data
  public static class RuleUpsertDTO {
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称不能超过 128 个字符")
    private String ruleName;

    /** DIRECTORY/TAG。 */
    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    private RuleConditionsDTO conditions;

    private Long targetDirectoryId;
    private Long targetTagId;
    private Integer priority;
  }

  @Data
  public static class RuleConditionsDTO {
    private List<String> assetTypes;
    private List<String> layerCodes;
    private List<String> domainCodes;
    private List<String> sourceTypes;

    @Size(max = 256, message = "名称正则不能超过 256 个字符")
    private String nameRegex;

    @Size(max = 64, message = "关键词不能超过 64 个字符")
    private String keyword;
  }

  /** 手动对账触发:空=全部已注册来源。 */
  @Data
  public static class ReconcileTriggerDTO {
    private List<String> sourceTypes;
  }

  /** 上架预检(body: ids[])→ 缺口清单 + 5 分钟 token。 */
  @Data
  public static class PrecheckDTO {
    @NotEmpty(message = "请至少选择一个资产")
    @Size(max = 200, message = "单次最多预检 200 个资产")
    private List<Long> assetIds;
  }

  /** 上架:token 必带(48005);有缺口时须 acceptRisk=true(48004)。 */
  @Data
  public static class PublishDTO {
    @NotEmpty(message = "请至少选择一个资产")
    @Size(max = 200, message = "单次最多上架 200 个资产")
    private List<Long> assetIds;

    @NotBlank(message = "请先执行上架预检并携带令牌")
    private String token;

    private boolean acceptRisk;
  }

  @Data
  public static class OfflineDTO {
    @NotEmpty(message = "请至少选择一个资产")
    @Size(max = 200, message = "单次最多下架 200 个资产")
    private List<Long> assetIds;

    @NotBlank(message = "下架原因必填")
    @Size(max = 512, message = "下架原因不能超过 512 个字符")
    private String reason;
  }

  @Data
  public static class IgnoreAssetsDTO {
    @NotEmpty(message = "请至少选择一个资产")
    @Size(max = 200, message = "单次最多忽略 200 个资产")
    private List<Long> assetIds;
  }

  /** 浏览上报:entry=catalog/search/detail 等入口标识,可空。 */
  @Data
  public static class ViewReportDTO {
    @Size(max = 64, message = "入口标识不能超过 64 个字符")
    private String entry;
  }
}
