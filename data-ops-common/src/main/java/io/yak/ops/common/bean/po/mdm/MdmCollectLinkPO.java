package io.yak.ops.common.bean.po.mdm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 采集落地链路绑定持久化对象(R1):来源 → 数据集成落地任务的反查锚点。 */
@Data
@TableName("yak_mdm_collect_link")
public class MdmCollectLinkPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 主数据实体(冗余,便于按实体列表)。 */
  private Long entityId;

  /** 主数据来源绑定 yak_mdm_source.id。 */
  private Long sourceId;

  /** 源数据源(松散 ID,冗余自来源)。 */
  private Long datasourceId;

  /** 平台库落地表名。 */
  private String landingTable;

  /** 数据集成离线任务定义 ID(反查锚点)。 */
  private Long jobDefinitionId;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
