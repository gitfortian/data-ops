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

/** Active-only UI counts and deletion-safe counts have distinct semantics. */
class ModelingLayerUsageReaderTest {

  @BeforeAll
  static void prepareMapping() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "modeling-layer-references"),
        ModelingModelPO.class);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void deletionCountIncludesRecoverableModelsWithinTrustedProject() {
    ModelingModelMapper mapper = mock(ModelingModelMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(42L);
    when(mapper.selectCount(any())).thenReturn(3L);
    ModelingLayerUsageReader reader = new ModelingLayerUsageReader(mapper, project);

    assertThat(reader.countPersistedLayerReferences("DWS_EXAMPLE")).isEqualTo(3L);

    ArgumentCaptor<LambdaQueryWrapper> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectCount(query.capture());
    LambdaQueryWrapper<ModelingModelPO> wrapper = query.getValue();
    assertThat(wrapper.getSqlSegment()).contains("project_id", "layer_code");
    assertThat(wrapper.getSqlSegment()).doesNotContain("deleted");
    assertThat(wrapper.getParamNameValuePairs().values()).contains(42L, "DWS_EXAMPLE");
  }

  @Test
  void projectMissingBlocksDeletionCountWithoutQueryingDatabase() {
    ModelingModelMapper mapper = mock(ModelingModelMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenThrow(new IllegalStateException("no project"));
    ModelingLayerUsageReader reader = new ModelingLayerUsageReader(mapper, project);

    assertThrows(IllegalStateException.class,
        () -> reader.countPersistedLayerReferences("DWS_EXAMPLE"));

    verify(mapper, never()).selectCount(any());
  }
}
