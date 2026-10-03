package io.yak.ops.business.job.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.core.project.ProjectContext;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class JdbcTaskExecutionJournalTest {
  @Test
  void retainsImmutableTerminalEvidenceAndIsolatesProjectAndTaskType() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:job-journal;MODE=MySQL;DB_CLOSE_DELAY=-1");
    new ResourceDatabasePopulator(new ClassPathResource(
        "db/migration/yak-job-runtime/V1__terminal_execution_evidence.sql")).execute(dataSource);
    AtomicReference<ProjectContext> project = new AtomicReference<>(new ProjectContext(1L, null));
    JdbcTaskExecutionJournal journal = new JdbcTaskExecutionJournal(dataSource, new ObjectMapper(),
        () -> Optional.ofNullable(project.get()));
    TaskExecution result = new TaskExecution("sql-one", "SUCCEEDED", null, Map.of("rows", 2));
    journal.save("SQL", "attempt-one", result);
    journal.save("SQL", "attempt-one", new TaskExecution("sql-two", "FAILED", "later", Map.of()));
    assertThat(journal.findByKey("SQL", "attempt-one")).contains(result);
    assertThat(journal.findById("sql-one")).contains(result);
    assertThat(journal.findByKey("SHELL", "attempt-one")).isEmpty();
    assertThatThrownBy(() -> journal.save("SQL", "running",
        new TaskExecution("sql-running", "RUNNING", null, Map.of()))).isInstanceOf(IllegalArgumentException.class);
    project.set(new ProjectContext(2L, null));
    assertThat(journal.findById("sql-one")).isEmpty();
    assertThat(journal.findByKey("SQL", "attempt-one")).isEmpty();
    journal.save("SQL", "attempt-one", new TaskExecution("sql-other-project", "SUCCEEDED", null, Map.of()));
    assertThat(journal.findByKey("SQL", "attempt-one")).get().extracting(TaskExecution::executionId).isEqualTo("sql-other-project");
  }
}
