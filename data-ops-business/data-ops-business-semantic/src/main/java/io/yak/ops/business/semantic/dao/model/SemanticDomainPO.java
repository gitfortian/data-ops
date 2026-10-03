package io.yak.ops.business.semantic.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 业务语义业务域持久化对象(树形,parent_id=0 表示根)。 */
@Data
@TableName("yak_semantic_domain")
public class SemanticDomainPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 业务域编码,项目内唯一,创建后不可改。 */
  private String domainCode;

  /** 业务域名称。 */
  private String domainName;

  /** 父域 ID,0=根。 */
  private Long parentId;

  /** 负责人。 */
  private String owner;

  /** 描述。 */
  private String description;

  /** 排序,小在前。 */
  private Integer sortOrder;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
