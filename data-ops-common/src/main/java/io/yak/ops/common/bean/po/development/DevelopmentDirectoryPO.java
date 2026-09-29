package io.yak.ops.common.bean.po.development;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 数据开发目录持久化对象。 */
@Data
@TableName("yak_dev_directory")
public class DevelopmentDirectoryPO {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;
  private Long projectId;
  private Long parentId;
  private String name;
  private Instant createTime;
  private Instant updateTime;
}
