package io.yak.ops.business.semantic.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.semantic.dao.mapper.SemanticStandardMapper;
import io.yak.ops.business.semantic.dao.model.SemanticStandardPO;
import io.yak.ops.business.semantic.repository.StandardRepositoryAdapter;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StandardMatchCandidateQueryTest {
  @Test void candidateSqlBindsTrustedProjectKindStatusAndHardLimit() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SemanticStandardPO.class);
    var mapper = mock(SemanticStandardMapper.class);
    var project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(42L);
    when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    new StandardRepositoryAdapter(mapper, project).searchTypeCandidates("id");
    var capture = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectList(capture.capture());
    var query = capture.getValue();
    assertTrue(query.getSqlSegment().contains("project_id"));
    assertTrue(query.getSqlSegment().contains("LIMIT 21"));
    assertTrue(query.getParamNameValuePairs().containsValue(42L));
    assertTrue(query.getParamNameValuePairs().containsValue("TYPE"));
    assertTrue(query.getParamNameValuePairs().containsValue("ENABLED"));
  }
  @Test void missingProjectReadsNoCatalog() {
    var mapper = mock(SemanticStandardMapper.class); var project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenThrow(new IllegalStateException("required"));
    assertThrows(IllegalStateException.class, () -> new StandardRepositoryAdapter(mapper, project).searchTypeCandidates(""));
    verifyNoInteractions(mapper);
  }
}
