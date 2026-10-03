package io.yak.ops.business.workflow.controller.v1;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.execution.WorkflowLauncher;
import io.yak.ops.business.workflow.schedule.WorkflowDefinitionScheduleGuard;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionCreateDTO;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;
import io.yak.ops.business.job.task.TaskRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkflowDefinitionBadRequestTest {
  @Test
  void emptyDraftReturnsHelpfulBadRequest() throws Exception {
    var definitions = new WorkflowDefinitionManager(mock(WorkflowRuntime.class), mock(TaskRegistry.class));
    var draft = definitions.create(new WorkflowDefinitionCreateDTO("空图测试", null));
    var launcher = mock(WorkflowLauncher.class);
    when(launcher.testRunDraft(eq(draft.id()), any())).thenAnswer(invocation -> definitions.testRun(draft.id()));
    var controller = new WorkflowDefinitionController(definitions, launcher, mock(WorkflowDefinitionScheduleGuard.class));
    MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new WorkflowDefinitionExceptionHandler()).build()
        .perform(post("/api/v1/workflows/definitions/" + draft.id() + "/test-run"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("请先配置至少一个任务节点"));
  }
}
