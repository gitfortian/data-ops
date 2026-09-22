package io.yak.ops.common.bean.po.modeling;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数仓建模模型目录持久化对象。 */
@Data
@TableName("yak_modeling_directory")
public class ModelingDirectoryPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 父目录 id,0 表示根。 */
  private Long parentId;

  /** 目录名称。 */
  private String name;

  /** 绑定的业务域(semantic 松散引用;目录随域自动生成)。 */
  private Long domainId;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
