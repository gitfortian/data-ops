package io.yak.ops.common.bean.po.mdm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 主数据记录持久化对象(统一主数据表,由主数据加工任务写入,MDM 只读)。 */
@Data
@TableName("yak_mdm_record")
public class MdmRecordPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 主数据实体。 */
  private Long entityId;

  /** 主数据唯一 ID(跨系统统一)。 */
  private String masterId;

  /** 属性值(键=属性编码)。 */
  private String attributes;

  /** 各系统原始 ID:{datasourceId: 原始ID}。 */
  private String sourceIds;

  /** 状态:ACTIVE/MERGED/DELETED。 */
  private String status;

  /** 版本,每次更新自增。 */
  private Integer version;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
