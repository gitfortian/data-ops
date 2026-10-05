package io.yak.ops.business.modeling.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelColumnMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelIndexMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.dao.model.ModelingModelColumnPO;
import io.yak.ops.business.modeling.dao.model.ModelingModelIndexPO;
import io.yak.ops.business.modeling.dao.model.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelStructureRepositoryLockingReadTest {

  private ModelingModelMapper modelMapper;
  private ModelingModelColumnMapper columnMapper;
  private ModelingModelIndexMapper indexMapper;
  private ModelStructureRepositoryAdapter repository;

  @BeforeEach
  void setUp() {
    for (Class<?> entity : java.util.List.of(
        ModelingModelPO.class, ModelingModelColumnPO.class, ModelingModelIndexPO.class)) {
      TableInfoHelper.initTableInfo(
          new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
    }
    modelMapper = mock(ModelingModelMapper.class);
    columnMapper = mock(ModelingModelColumnMapper.class);
    indexMapper = mock(ModelingModelIndexMapper.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(7L);
    repository = new ModelStructureRepositoryAdapter(
        modelMapper, columnMapper, indexMapper, project);
  }

  @Test
  void lockingReadsCoverModelAttributesColumnsAndIndexes() {
    ModelingModelPO model = new ModelingModelPO();
    model.setId(42L);
    model.setProjectId(7L);
    model.setDeleted(false);
    when(modelMapper.selectOne(any())).thenReturn(model);
    when(columnMapper.selectList(any())).thenReturn(java.util.List.of());
    when(indexMapper.selectList(any())).thenReturn(java.util.List.of());

    repository.findStructureAttributesForUpdate(42L);
    repository.findColumnsForUpdate(42L);
    repository.findIndexesForUpdate(42L);

    ArgumentCaptor<LambdaQueryWrapper<ModelingModelPO>> modelQuery =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(modelMapper).selectOne(modelQuery.capture());
    assertThat(modelQuery.getValue().getSqlSegment()).contains("FOR UPDATE");
    ArgumentCaptor<LambdaQueryWrapper<ModelingModelColumnPO>> columnQuery =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(columnMapper).selectList(columnQuery.capture());
    assertThat(columnQuery.getValue().getSqlSegment()).contains("FOR UPDATE");
    ArgumentCaptor<LambdaQueryWrapper<ModelingModelIndexPO>> indexQuery =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(indexMapper).selectList(indexQuery.capture());
    assertThat(indexQuery.getValue().getSqlSegment()).contains("FOR UPDATE");
  }

  @Test
  void standardFieldUpdateLocksLiveModelBeforeChangingColumn() {
    ModelingModelPO model = new ModelingModelPO();
    model.setId(42L);
    model.setProjectId(7L);
    model.setDeleted(false);
    when(modelMapper.selectOne(any())).thenReturn(model);

    repository.updateColumnStdField(42L, "customer_id", 9L);

    ArgumentCaptor<LambdaQueryWrapper<ModelingModelPO>> query =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(modelMapper).selectOne(query.capture());
    assertThat(query.getValue().getSqlSegment()).contains("FOR UPDATE");
    verify(columnMapper).update(any(), any());
  }
}
