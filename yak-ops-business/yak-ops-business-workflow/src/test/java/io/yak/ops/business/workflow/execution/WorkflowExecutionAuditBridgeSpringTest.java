package io.yak.ops.business.workflow.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

class WorkflowExecutionAuditBridgeSpringTest {

  @Test
  void componentScanSelectsAutowiredConstructorsForAuditComponents() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(TestConfiguration.class)) {
      assertThat(context.getBean(WorkflowExecutionAuditBridge.class)).isNotNull();
      assertThat(context.getBean(WorkflowExecutionControlAuditCoordinator.class)).isNotNull();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ComponentScan(
      basePackageClasses = WorkflowExecutionAuditBridge.class,
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = {
                WorkflowExecutionAuditBridge.class,
                WorkflowExecutionControlAuditCoordinator.class
              }))
  static class TestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    WorkflowRuntime workflowRuntime() {
      return mock(WorkflowRuntime.class);
    }
  }
}
