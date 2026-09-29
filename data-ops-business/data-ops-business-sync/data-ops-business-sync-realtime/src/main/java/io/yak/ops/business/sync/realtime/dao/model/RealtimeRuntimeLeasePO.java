package io.yak.ops.business.sync.realtime.dao.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_realtime_runtime_lease")
public class RealtimeRuntimeLeasePO {
  @TableId
  private Integer id;
  private String leaseOwner;
  private LocalDateTime leaseUntil;
  private LocalDateTime updateTime;
}
