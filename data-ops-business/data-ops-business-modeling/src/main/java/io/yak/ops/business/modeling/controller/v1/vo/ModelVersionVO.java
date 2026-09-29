package io.yak.ops.business.modeling.controller.v1.vo;

import java.time.LocalDateTime;

/** 模型版本摘要(列表用,不含结构快照)。 */
public record ModelVersionVO(
    Long id,
    Long modelId,
    int versionNo,
    int columnCount,
    String checksum,
    String publishedBy,
    LocalDateTime publishTime) {
}
