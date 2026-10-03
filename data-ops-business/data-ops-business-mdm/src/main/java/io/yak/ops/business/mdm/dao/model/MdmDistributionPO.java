package io.yak.ops.business.mdm.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 主数据分发配置持久化对象(实体级,目标系统+方式+频率,ticket 58)。 */
@Data
@TableName("yak_mdm_distribution")
public class MdmDistributionPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 主数据实体。 */
  private Long entityId;

  /** 目标系统编码。 */
  private String targetSystem;

  /** 目标系统名称。 */
  private String targetName;

  /** 分发方式:API/MESSAGE/FILE。 */
  private String distributeMode;

  /** 分发频率:MANUAL/DAILY/HOURLY。 */
  private String distributeFreq;

  /** 分发范围:FULL/INCREMENTAL。 */
  private String distributeScope;

  /** 状态:DRAFT/ACTIVE/DISABLED。 */
  private String status;

  /** 最近分发时间。 */
  private LocalDateTime lastDistributeTime;

  /** 最近分发条数。 */
  private Integer lastDistributeCount;

  /** 最近分发失败条数。 */
  private Integer lastDistributeFail;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
