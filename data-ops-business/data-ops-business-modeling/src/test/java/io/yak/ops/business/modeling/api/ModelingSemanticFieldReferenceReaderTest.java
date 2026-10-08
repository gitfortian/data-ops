package io.yak.ops.business.modeling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelColumnMapper;
import io.yak.ops.business.modeling.dao.model.ModelingModelColumnPO;
import io.yak.ops.core.project.CurrentProject;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Verify Modeling's reverse-reference query cannot read outside the current project. */
class ModelingSemanticFieldReferenceReaderTest {

  @BeforeAll
  static void prepareMapping() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "modeling-ref-test"),
        ModelingModelColumnPO.class);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void countsPersistedFieldBindingsWithTrustedProjectScope() {
    ModelingModelColumnMapper mapper = mock(ModelingModelColumnMapper.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(42L);
    when(mapper.selectCount(any())).thenReturn(3L);

    ModelingSemanticFieldReferenceReader reader =
        new ModelingSemanticFieldReferenceReader(mapper, currentProject);

    assertThat(reader.countFieldReferences(35L)).isEqualTo(3L);

    ArgumentCaptor<LambdaQueryWrapper> query =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectCount(query.capture());
    LambdaQueryWrapper<ModelingModelColumnPO> conditions = query.getValue();
    // Field ID and project identity must both be constrained. The mock mapper
    // does not execute SQL, so use the test MyBatis mapping's column names.
    assertThat(conditions.getSqlSegment()).contains("projectId", "stdFieldId");
    assertThat(conditions.getParamNameValuePairs().values()).contains(42L, 35L);
  }

  @Test
  void doesNotTreatUnavailableProjectContextAsZeroReferences() {
    ModelingModelColumnMapper mapper = mock(ModelingModelColumnMapper.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenThrow(new IllegalStateException("no project"));

    ModelingSemanticFieldReferenceReader reader =
        new ModelingSemanticFieldReferenceReader(mapper, currentProject);

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> reader.countFieldReferences(35L));
    verify(mapper, org.mockito.Mockito.never()).selectCount(any());
  }
}
