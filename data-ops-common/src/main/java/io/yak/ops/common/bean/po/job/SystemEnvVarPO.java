package io.yak.ops.common.bean.po.job;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 系统环境变量持久化对象。 */
@Data
@TableName("yak_system_env_var")
public class SystemEnvVarPO {

  /** 变量键（主键）。 */
  @TableId(type = IdType.INPUT)
  private String varKey;

  /** 变量值。 */
  private String varValue;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
