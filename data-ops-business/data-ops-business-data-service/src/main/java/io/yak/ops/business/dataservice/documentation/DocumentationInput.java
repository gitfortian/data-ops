package io.yak.ops.business.dataservice.documentation;

import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ResponseFieldDoc;
import java.util.List;

public record DocumentationInput(
    List<ParameterDoc> parameters,
    List<ResponseFieldDoc> responseFields) {}
