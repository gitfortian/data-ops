package io.yak.ops.common.bean.po.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 资产台账(核心)。asset_key 直接复用源域血缘登记键生成器(D6:与
 * yak_metadata_asset 键同源,不造第二套键);MANUAL 资产为 manual:{code}。
 */
@Data
@TableName("yak_asset_item")
public class AssetItemPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  /** 血缘同源键,如 modeling:model:{id} / metric:{id} / dataset:{id} / manual:{code}。 */
  private String assetKey;
  /** MODEL/METRIC/DATASET/DASHBOARD/CHART/TASK/MANUAL。 */
  private String sourceType;
  /** 源域主键/编码(字符串容纳)。 */
  private String sourceId;
  /** 展示类型 TABLE/METRIC/.../DOC。 */
  private String assetType;
  /** 快照,可编辑;META_CHANGED 确认时更新。 */
  private String name;
  private String description;
  /** 快照自源域(semantic 字典值)。 */
  private String layerCode;
  private String domainCode;
  /** 主目录(一资产一个)。 */
  private Long directoryId;
  /** 统一负责人,唯一事实源(D4)。 */
  private String owner;
  /** PENDING/PUBLISHED/OFFLINE/IGNORED/SOURCE_GONE。 */
  private String status;
  /** 源域 descriptor 指纹 → META_CHANGED 判定。 */
  private String contentHash;
  private LocalDateTime sourceUpdatedAt;
  /** 快照自 security;详情页实时值优先。 */
  private String securityLevelCode;
  /** 派生缓存(每日重算,D7)。 */
  private Integer healthScore;
  /** A/B/C/D。 */
  private String healthGrade;
  /** 评分明细(每项得分与缺口)。 */
  private String healthDetail;
  /** 派生缓存(每日聚合)。 */
  @TableField("view_count_30d")
  private Integer viewCount30d;
  /** MANUAL 资产访问入口。 */
  private String accessUri;
  private LocalDateTime firstListedAt;
  private LocalDateTime lastListedAt;
  private LocalDateTime lastOfflineAt;
  private String lastOfflineReason;
  /** 最近一次对账确认存在时间(SOURCE_GONE 判定窗口用)。 */
  private LocalDateTime reconciledAt;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
