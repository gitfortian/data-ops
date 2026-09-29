package io.yak.ops.common.bean.po.mdm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 主数据订阅持久化对象(系统订阅实体变更,ticket 59)。 */
@Data
@TableName("yak_mdm_subscription")
public class MdmSubscriptionPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;

  private Long entityId;

  private String subscriberCode;

  private String subscriberName;

  private String notifyMode;

  private String status;

  private String createdBy;

  private LocalDateTime createTime;

  private LocalDateTime updateTime;
}
