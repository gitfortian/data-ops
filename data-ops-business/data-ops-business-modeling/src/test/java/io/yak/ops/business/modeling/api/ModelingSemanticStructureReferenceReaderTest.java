package io.yak.ops.business.modeling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.dao.model.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Project-scoped process/domain references are owned and counted by Modeling. */
class ModelingSemanticStructureReferenceReaderTest {

  @BeforeAll
  static void prepareMapping() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "semantic-structure-ref-test"),
        ModelingModelPO.class);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void processReferencesUseCurrentProjectAndIncludeRecoverableModels() {
    ModelingModelMapper mapper = mock(ModelingModelMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(42L);
    when(mapper.selectCount(any())).thenReturn(3L);
    ModelingSemanticStructureReferenceReader reader =
        new ModelingSemanticStructureReferenceReader(mapper, project);

    assertThat(reader.countProcessReferences(9L)).isEqualTo(3L);

    ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectCount(captor.capture());
    LambdaQueryWrapper<ModelingModelPO> query = captor.getValue();
    assertThat(query.getSqlSegment()).contains("project_id", "process_id");
    assertThat(query.getSqlSegment()).doesNotContain("deleted");
    assertThat(query.getParamNameValuePairs().values()).contains(42L, 9L);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void domainReferencesUseCurrentProjectAndIncludeRecoverableModels() {
    ModelingModelMapper mapper = mock(ModelingModelMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(42L);
    when(mapper.selectCount(any())).thenReturn(2L);
    ModelingSemanticStructureReferenceReader reader =
        new ModelingSemanticStructureReferenceReader(mapper, project);

    assertThat(reader.countDomainReferences(5L)).isEqualTo(2L);

    ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectCount(captor.capture());
    LambdaQueryWrapper<ModelingModelPO> query = captor.getValue();
    assertThat(query.getSqlSegment()).contains("project_id", "domain_id");
    assertThat(query.getSqlSegment()).doesNotContain("deleted");
    assertThat(query.getParamNameValuePairs().values()).contains(42L, 5L);
  }

  @Test
  void missingProjectContextNeverMeansZeroReferences() {
    ModelingModelMapper mapper = mock(ModelingModelMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenThrow(new IllegalStateException("no Project Space"));
    ModelingSemanticStructureReferenceReader reader =
        new ModelingSemanticStructureReferenceReader(mapper, project);

    assertThrows(IllegalStateException.class, () -> reader.countProcessReferences(9L));
    assertThrows(IllegalStateException.class, () -> reader.countDomainReferences(5L));
    verify(mapper, never()).selectCount(any());
  }

  @Test
  void modelingQueryErrorMustPropagateToDeletingDomain() {
    ModelingModelMapper mapper = mock(ModelingModelMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(42L);
    when(mapper.selectCount(any())).thenThrow(new IllegalStateException("database unavailable"));
    ModelingSemanticStructureReferenceReader reader =
        new ModelingSemanticStructureReferenceReader(mapper, project);

    assertThrows(IllegalStateException.class, () -> reader.countDomainReferences(5L));
  }
}
