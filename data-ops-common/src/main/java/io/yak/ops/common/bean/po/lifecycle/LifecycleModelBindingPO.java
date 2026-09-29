package io.yak.ops.common.bean.po.lifecycle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 模型 TTL 绑定:行存在=覆盖分层默认;软删=回到继承。 */
@Data
@TableName("yak_lc_model_binding")
public class LifecycleModelBindingPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long modelId;
  private Long policyId;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
