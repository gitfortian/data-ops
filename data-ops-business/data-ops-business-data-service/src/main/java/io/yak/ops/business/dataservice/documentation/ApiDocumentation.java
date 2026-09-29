package io.yak.ops.business.dataservice.documentation;

import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ResponseFieldDoc;
import java.time.LocalDateTime;
import java.util.List;

public record ApiDocumentation(
    Long apiId,
    String name,
    String runtimePath,
    String authMode,
    String description,
    boolean documented,
    boolean schemaStale,
    List<ParameterDoc> parameters,
    List<ResponseFieldDoc> responseFields,
    LocalDateTime updateTime) {}
