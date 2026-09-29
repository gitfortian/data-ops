package io.yak.ops.common.bean.po.modeling;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/** 模型版本快照持久化对象(不可变)。 */
@Data
@TableName("yak_modeling_model_version")
public class ModelingModelVersionPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属项目空间。 */
  private Long projectId;

  /** 所属模型。 */
  private Long modelId;

  /** 版本号(模型内递增)。 */
  private Integer versionNo;

  /** 完整结构快照(JSON)。 */
  @ToString.Exclude
  private String structureJson;

  /** 发布时模型元数据快照(JSON：名称/描述/分层/业务域/方言)。 */
  @ToString.Exclude
  private String metaJson;

  /** 字段数量。 */
  private Integer columnCount;

  /** 结构 SHA-256 摘要(幂等判定)。 */
  private String checksum;

  /** 发布人。 */
  private String publishedBy;

  /** 发布时间。 */
  private LocalDateTime publishTime;
}
