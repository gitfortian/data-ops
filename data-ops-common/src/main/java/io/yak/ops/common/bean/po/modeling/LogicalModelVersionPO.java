package io.yak.ops.common.bean.po.modeling;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("yak_modeling_logical_model_version")
public class LogicalModelVersionPO {

    private Long id;

    private Long modelId;

    private Integer versionNo;

    private String status;

    private String snapshot;

    private String createdBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
