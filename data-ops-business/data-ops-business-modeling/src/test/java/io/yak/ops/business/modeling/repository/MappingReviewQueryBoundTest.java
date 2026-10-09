package io.yak.ops.business.modeling.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.modeling.dao.mapper.ModelingColumnMappingMapper;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MappingReviewQueryBoundTest {
  @Test @SuppressWarnings({"unchecked", "rawtypes"})
  void sqlScopesOrdersAndLimitsBeforeRowsEnterMemory() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ModelingColumnMappingPO.class);
    var mapper = mock(ModelingColumnMappingMapper.class); var project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(42L); when(mapper.selectList(any())).thenReturn(List.of());
    new MappingRepositoryAdapter(mapper, project).listByModelForReview(7L);
    ArgumentCaptor<LambdaQueryWrapper<ModelingColumnMappingPO>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(query.capture()); String sql = query.getValue().getSqlSegment();
    assertTrue(sql.contains("project_id")); assertTrue(sql.contains("model_id"));
    assertTrue(sql.contains("ORDER BY id ASC")); assertTrue(sql.endsWith("LIMIT 101 FOR UPDATE"));
    assertEquals(java.util.Set.of(42L, 7L), new java.util.HashSet<>(query.getValue().getParamNameValuePairs().values()));
  }
}
