package io.yak.ops.business.workflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.dao.WorkflowCatalogDao;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.repository.WorkflowAuditCorrelationRepository;
import io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeAttemptPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowLayeringConventionTest {

  @Test
  void repositoryBoundariesDoNotExposeHttpOrPersistenceContracts() {
    assertCleanRepositoryBoundary(
        List.of(
            WorkflowDefinitionRepository.class,
            WorkflowRuntimeRepository.class,
            WorkflowAuditCorrelationRepository.class));
  }

  @Test
  void daoBoundariesDoNotExposeHttpContracts() {
    for (Class<?> type : List.of(
        WorkflowCatalogDao.class,
        WorkflowExecutionDao.class,
        WorkflowScheduleDao.class,
        WorkflowScheduleTriggerDao.class,
        WorkflowBackfillDao.class)) {
      for (Method method : type.getMethods()) {
        assertThat(signature(method))
            .as("DAO transport boundary: %s#%s", type.getSimpleName(), method.getName())
            .doesNotContain(".bean.dto.workflow")
            .doesNotContain(".bean.vo.workflow");
      }
    }
  }

  @Test
  void tablePosStayOneToOneWithWorkflowTables() {
    assertTable(WorkflowDefinitionPO.class, "yak_workflow_definition");
    assertTable(WorkflowVersionPO.class, "yak_workflow_version");
    assertTable(WorkflowExecutionPO.class, "yak_workflow_execution");
    assertTable(WorkflowNodeExecutionPO.class, "yak_workflow_node_execution");
    assertTable(WorkflowNodeAttemptPO.class, "yak_workflow_node_attempt");
    assertTable(WorkflowSchedulePO.class, "yak_workflow_schedule");
    assertTable(WorkflowScheduleTriggerPO.class, "yak_workflow_schedule_trigger");
    assertTable(WorkflowBackfillPO.class, "yak_workflow_backfill");
  }

  private void assertCleanRepositoryBoundary(List<Class<?>> types) {
    for (Class<?> type : types) {
      for (Method method : type.getMethods()) {
        assertThat(signature(method))
            .as("Repository boundary: %s#%s", type.getSimpleName(), method.getName())
            .doesNotContain(".bean.dto.workflow")
            .doesNotContain(".bean.vo.workflow")
            .doesNotContain(".bean.po.workflow")
            .doesNotContain("com.baomidou.mybatisplus");
      }
    }
  }

  private String signature(Method method) {
    StringBuilder signature = new StringBuilder(method.getGenericReturnType().getTypeName());
    for (var parameter : method.getGenericParameterTypes()) {
      signature.append('|').append(parameter.getTypeName());
    }
    return signature.toString();
  }

  private void assertTable(Class<?> type, String expected) {
    TableName tableName = type.getAnnotation(TableName.class);
    assertThat(tableName).as(type.getSimpleName() + " @TableName").isNotNull();
    assertThat(tableName.value()).isEqualTo(expected);
  }
}
