package io.yak.ops.common.bean.po.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_quality_template_folder")
public class QualityTemplateFolderPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long parentId;
  private String folderName;
  private Integer sortOrder;
  private Boolean deleted;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
