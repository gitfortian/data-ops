package io.yak.ops.common.bean.po.development;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 数据开发树节点持久化对象。 */
@Data
@TableName("yak_dev_node")
public class DevelopmentNodePO {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;
  private String name;
  private String type;
  private Long projectId;
  private Long directoryId;
  private Boolean configured;
  @TableLogic private Boolean deleted;
  private String updatedBy;
  private Instant createTime;
  private Instant updateTime;
}
