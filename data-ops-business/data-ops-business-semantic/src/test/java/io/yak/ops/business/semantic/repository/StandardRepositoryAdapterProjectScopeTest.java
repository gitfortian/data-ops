package io.yak.ops.business.semantic.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.semantic.dao.mapper.SemanticStandardMapper;
import io.yak.ops.business.semantic.dao.model.SemanticStandardPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Regression guards for project scoping on standard evidence reads.
 *
 * These tests verify that the current server-side project ID is included in
 * repository query parameters; they are not a substitute for database-level
 * cross-project integration tests.
 */
class StandardRepositoryAdapterProjectScopeTest {
  private static final long CURRENT_PROJECT_ID = 7L;

  private SemanticStandardMapper mapper;
  private StandardRepositoryAdapter repository;

  @BeforeAll
  static void initializeLambdaMetadata() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""),
        SemanticStandardPO.class);
  }

  @BeforeEach
  void setUp() {
    mapper = mock(SemanticStandardMapper.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(CURRENT_PROJECT_ID);
    repository = new StandardRepositoryAdapter(mapper, currentProject);
  }

  @Test
  void findByIdIncludesCurrentProjectInQuery() {
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

    repository.findById(11L);

    ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).selectOne(captor.capture());
    assertHasCurrentProject(captor.getValue());
  }

  @Test
  void listByCodeSetCodeIncludesCurrentProjectInQuery() {
    when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());

    repository.listByCodeSetCode("ORDER_STATUS");

    ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).selectList(captor.capture());
    assertHasCurrentProject(captor.getValue());
  }

  @Test
  void bothCodeSetExistenceQueriesIncludeCurrentProjectInQuery() {
    when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);

    repository.existsEnabledByCodeSet("ORDER_STATUS");

    ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper, times(2)).selectCount(captor.capture());
    assertEquals(2, captor.getAllValues().size());
    captor.getAllValues().forEach(StandardRepositoryAdapterProjectScopeTest::assertHasCurrentProject);
  }

  private static void assertHasCurrentProject(Wrapper<?> wrapper) {
    AbstractWrapper<?, ?, ?> query = (AbstractWrapper<?, ?, ?>) wrapper;
    String sqlSegment = query.getSqlSegment();
    Map<String, Object> parameters = query.getParamNameValuePairs();
    boolean hasCurrentProjectId =
        parameters.values().stream()
            .anyMatch(
                value ->
                    value instanceof Number number
                        && number.longValue() == CURRENT_PROJECT_ID);
    assertTrue(
        sqlSegment.contains("project_id"),
        "repository query must constrain project_id; sqlSegment=" + sqlSegment);
    assertTrue(
        hasCurrentProjectId,
        "repository query must bind the trusted current project ID "
            + CURRENT_PROJECT_ID
            + "; sqlSegment="
            + sqlSegment
            + "; parameters="
            + parameters);
  }
}
