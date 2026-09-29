package io.yak.ops.business.digitalscreen.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

@Data
@TableName("yak_digital_screen_version")
public class DigitalScreenVersionPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long screenId;
  private Integer versionNo;
  private Long sourceRevision;
  private String nameSnapshot;
  private String descriptionSnapshot;
  private String templateIdSnapshot;
  private Integer templateVersionSnapshot;
  private String bindingsJson;
  private Timestamp publishedTime;
  private Timestamp createTime;
}
