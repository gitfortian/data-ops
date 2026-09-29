package io.yak.ops.business.datasource.execution.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.datasource.dao.SqlExecutionAuditDao;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditPO;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditQuery;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SqlExecutionAuditReaderProjectScopeTest {

  @Mock private SqlExecutionAuditDao auditDao;

  @Test
  void injectsCurrentProjectIntoAuditPageQuery() {
    Page<SqlExecutionAuditPO> page = Page.of(1, 20);
    page.setRecords(List.of());
    page.setTotal(0L);
    when(auditDao.selectPage(any(SqlExecutionAuditQuery.class))).thenReturn(page);

    SqlExecutionAuditReader reader = new SqlExecutionAuditReader(auditDao, project(7L));
    reader.page(null);

    ArgumentCaptor<SqlExecutionAuditQuery> queryCaptor =
        ArgumentCaptor.forClass(SqlExecutionAuditQuery.class);
    verify(auditDao).selectPage(queryCaptor.capture());
    assertThat(queryCaptor.getValue().getProjectId()).isEqualTo(7L);
  }

  @Test
  void rejectsAuditReadsWithoutCurrentProject() {
    CurrentProject missingProject = Optional::empty;
    SqlExecutionAuditReader reader = new SqlExecutionAuditReader(auditDao, missingProject);

    assertThatThrownBy(() -> reader.page(null))
        .isInstanceOf(ProjectContextException.class);
  }

  private static CurrentProject project(long projectId) {
    return () -> Optional.of(new ProjectContext(projectId, "Project " + projectId));
  }
}
