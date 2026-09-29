package io.yak.ops.common.bean.po.lifecycle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 生命周期模块设置(KV:成本单价等)。 */
@Data
@TableName("yak_lc_setting")
public class LifecycleSettingPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private String settingKey;
  private String settingValue;
  private LocalDateTime updateTime;
}
