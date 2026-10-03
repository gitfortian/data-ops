package io.yak.ops.business.lifecycle.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** TTL 策略(生命周期唯一事实源,D1)。 */
@Data
@TableName("yak_lc_policy")
public class LifecyclePolicyPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private String policyCode;
  private String policyName;
  /** LAYER_DEFAULT/CUSTOM。 */
  private String scopeType;
  /** 仅层默认策略持有。 */
  private String layerCode;
  /** DAY/MONTH/YEAR。 */
  private String partitionGranularity;
  /** 热窗口天数,NULL=无热段。 */
  private Integer hotDays;
  /** 冷边界天数(≥hot)。 */
  private Integer coldDays;
  /** 删除边界天数,NULL=永久保留。 */
  private Integer destroyDays;
  /** 1=模板初始化产生(不可删,可改)。 */
  private Boolean builtin;
  private String status;
  /** 发布态 DRAFT/PUBLISHED/OFFLINE（io.yak.ops.common.enums.PublishState），与 status 开关正交。 */
  private String publishState;
  /** 草稿修订号，每次编辑自增。 */
  private Integer draftRevision;
  /** 当前发布快照 id；消费方只读该快照内容。 */
  private Long publishedVersionId;
  /** 最大版本号（展示冗余）。 */
  private Integer latestVersionNo;
  private String remark;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
