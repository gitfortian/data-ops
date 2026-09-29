package io.yak.ops.business.datasource.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 巡检 worker:上下文先于探活、单源/单项目失败不中断、disabled 与重入跳过。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSourceHealthProbeWorkerTest {

  private final DataSourceRepository repository = mock(DataSourceRepository.class);
  private final DataSourceReader reader = mock(DataSourceReader.class);
  private final DataSourceConnectionTester tester = mock(DataSourceConnectionTester.class);
  private final DataSourceProperties properties = new DataSourceProperties();
  private final ProjectContextScope projectScope = mock(ProjectContextScope.class);
  private final DataSourceHealthProbeWorker worker =
      new DataSourceHealthProbeWorker(repository, reader, tester, properties, projectScope);

  @BeforeEach
  void runProjectScopeInline() {
    doAnswer(
            invocation -> {
              invocation.getArgument(1, Runnable.class).run();
              return null;
            })
        .when(projectScope)
        .run(any(), any());
  }

  @Test
  void probesEverySavedDatasourceWithItsOwnProjectContext() {
    DataSourceDefinition first = definition(11L);
    DataSourceDefinition second = definition(12L);
    when(repository.distinctProjectIds()).thenReturn(List.of(7L, 8L));
    when(reader.findAll(null)).thenReturn(List.of(first), List.of(second));

    worker.probeAllProjects();

    ArgumentCaptor<ProjectContext> contexts = ArgumentCaptor.forClass(ProjectContext.class);
    verify(projectScope, times(2)).run(contexts.capture(), any());
    assertThat(contexts.getAllValues()).extracting(ProjectContext::projectId)
        .containsExactly(7L, 8L);
    verify(tester).testSaved(11L);
    verify(tester).testSaved(12L);
  }

  @Test
  void oneFailingDatasourceDoesNotAbortTheSweep() {
    DataSourceDefinition broken1 = definition(11L);
    DataSourceDefinition healthy = definition(12L);
    DataSourceDefinition broken2 = definition(13L);
    when(repository.distinctProjectIds()).thenReturn(List.of(7L));
    when(reader.findAll(null))
        .thenReturn(List.of(broken1, healthy, broken2));
    doThrow(new IllegalStateException("connect timeout")).when(tester).testSaved(11L);
    doThrow(new IllegalStateException("connect refused")).when(tester).testSaved(13L);

    worker.probeAllProjects();

    verify(tester).testSaved(12L);
  }

  @Test
  void oneBrokenProjectDoesNotBlockOtherProjects() {
    DataSourceDefinition inSecondProject = definition(21L);
    when(repository.distinctProjectIds()).thenReturn(List.of(7L, 8L));
    doThrow(new RuntimeException("project 7 gone"))
        .when(projectScope)
        .run(eq(new ProjectContext(7L, null)), any());
    when(reader.findAll(null)).thenReturn(List.of(inSecondProject));

    worker.probeAllProjects();

    verify(tester).testSaved(21L);
  }

  @Test
  void disabledProbeTouchesNothing() {
    properties.getHealthProbe().setEnabled(false);

    worker.probeAllProjects();

    verifyNoInteractions(repository, reader, tester, projectScope);
  }

  @Test
  void reentrantTickSkipsInsteadOfRunningTwice() {
    DataSourceDefinition first = definition(11L);
    DataSourceDefinition second = definition(12L);
    when(repository.distinctProjectIds()).thenReturn(List.of(7L));
    when(reader.findAll(null)).thenReturn(List.of(first, second));
    doAnswer(
            invocation -> {
              // 模拟调度线程重叠:探活期间再次触发一轮,内层应直接跳过。
              worker.probeAllProjects();
              return null;
            })
        .when(tester)
        .testSaved(11L);

    worker.probeAllProjects();

    verify(repository, times(1)).distinctProjectIds();
    verify(tester, times(1)).testSaved(12L);
  }

  private static DataSourceDefinition definition(Long id) {
    DataSourceDefinition definition = mock(DataSourceDefinition.class);
    when(definition.getId()).thenReturn(id);
    return definition;
  }
}
