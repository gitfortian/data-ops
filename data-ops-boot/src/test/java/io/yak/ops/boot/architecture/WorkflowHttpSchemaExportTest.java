package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Exports the compiled HTTP DTOs; no server, database or historical example schema is used. */
class WorkflowHttpSchemaExportTest {
  @Test
  void exportWorkflowHttpSchemas() throws Exception {
    var schemas = new TreeMap<String, io.swagger.v3.oas.models.media.Schema>();
    schemas.putAll(ModelConverters.getInstance().readAll(WorkflowRunDTO.class));
    schemas.putAll(ModelConverters.getInstance().readAll(WorkflowInstanceVO.class));
    assertThat(schemas).containsKeys("WorkflowRunDTO", "WorkflowInstanceVO");
    assertThat(schemas.get("WorkflowInstanceVO").getProperties()).containsKeys("id", "nodes", "testRun");
    Path output = Path.of("target/contracts/workflow-http-schema.json");
    Files.createDirectories(output.getParent());
    Files.writeString(output, Json.mapper().writeValueAsString(schemas), StandardCharsets.UTF_8);
  }
}
