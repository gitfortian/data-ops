package io.yak.ops.business.digitalscreen.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

@Data
@TableName("yak_digital_screen")
public class DigitalScreenPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String name;
  private String description;
  private String templateId;
  private Integer templateVersion;
  private String status;
  private String bindingsJson;
  private Long revision;
  private Long publishedRevision;
  private Long publishedVersionId;
  private Integer publishedVersionNo;
  private Timestamp publishedTime;
  private Timestamp createTime;
  private Timestamp updateTime;
}
