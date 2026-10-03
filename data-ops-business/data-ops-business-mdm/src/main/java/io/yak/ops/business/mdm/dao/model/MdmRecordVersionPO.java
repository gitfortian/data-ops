package io.yak.ops.business.mdm.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 主数据记录版本快照持久化对象(R4,审批通过即快照,uk=(project,entity,master,version) 幂等)。 */
@Data
@TableName("yak_mdm_record_version")
public class MdmRecordVersionPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  private Long entityId;

  private String masterId;

  private Integer version;

  private String attributes;

  private String status;

  private Long changeId;

  private String operator;

  private LocalDateTime createTime;
}
