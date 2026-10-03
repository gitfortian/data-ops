package io.yak.ops.business.asset.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 编目规则(自动归目录/打标签;启用前必须试跑,D10)。 */
@Data
@TableName("yak_asset_assign_rule")
public class AssetAssignRulePO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private String ruleName;
  /** DIRECTORY/TAG。 */
  private String ruleType;
  /** {assetTypes,layerCodes,domainCodes,nameRegex,keyword,sourceTypes},空=通配,AND。 */
  private String conditions;
  /** 按 ruleType 二选一。 */
  private Long targetDirectoryId;
  private Long targetTagId;
  /** 小者优先;同类型首条命中即停。 */
  private Integer priority;
  /** 默认 0,试跑通过后才可启用。 */
  private Boolean enabled;
  /** 最近试跑/重应用命中数。 */
  private Integer lastApplyHit;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
