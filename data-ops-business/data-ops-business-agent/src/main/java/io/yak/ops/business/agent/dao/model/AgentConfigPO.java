package io.yak.ops.business.agent.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Agent 运行时动态配置 PO（per-key，热更新）。 */
@Data
@TableName("yak_config")
public class AgentConfigPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String configKey;

  private String configValue;

  private String description;

  private LocalDateTime updateTime;
}
