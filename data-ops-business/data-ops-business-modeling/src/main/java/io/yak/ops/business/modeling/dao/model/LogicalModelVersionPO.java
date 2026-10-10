package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("yak_modeling_logical_model_version")
public class LogicalModelVersionPO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long modelId;

    private Integer versionNo;

    private String status;

    private String snapshot;

    private String createdBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
