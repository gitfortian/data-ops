package io.yak.ops.common.bean.po.semantic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 业务语义标准字段持久化对象(全局字段库;std_* 为标准松散引用)。 */
@Data
@TableName("yak_semantic_field")
public class SemanticFieldPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 字段编码,项目内唯一,创建后不可改。 */
  private String fieldCode;

  /** 字段名称。 */
  private String fieldName;

  /** 角色:PROCESS/DIMENSION/METRIC。 */
  private String role;

  /** 状态:ENABLED/DISABLED(停用不出现在字段集/绑定/派生)。 */
  private String status;

  /** 生效类型:引用类型标准时为 std_type 快照(服务端同步)。 */
  private String dataType;

  /** 类型标准引用。 */
  private Long stdTypeId;

  /** 单位标准引用。 */
  private Long stdUnitId;

  /** 口径标准引用。 */
  private Long stdCaliberId;

  /** 码集编码引用(CODE 类标准的 code_set_code)。 */
  private String stdCodeSetCode;

  /** 安全标准引用。 */
  private Long stdSecurityId;

  /** 业务描述。 */
  private String businessDesc;

  /** 来源:PRESET/MANUAL/CAPTURE。 */
  private String source;

  /** 乐观版本,每次修改自增。 */
  private Integer version;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
