package io.yak.ops.business.semantic.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.yak.ops.business.semantic.dao.mapper.SemanticStandardMapper;
import io.yak.ops.business.semantic.dao.model.SemanticStandardPO;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandardRepositoryAdapterTest {
  private SemanticStandardMapper mapper;
  private CurrentProject currentProject;
  private StandardRepositoryAdapter repository;

  @BeforeEach
  void setUp() {
    mapper = mock(SemanticStandardMapper.class);
    currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(7L);
    repository = new StandardRepositoryAdapter(mapper, currentProject);
  }

  @Test
  void unknownStoredStatusIsNotSilentlyPromotedToEnabled() {
    SemanticStandardPO row = new SemanticStandardPO();
    row.setId(11L);
    row.setProjectId(7L);
    row.setKind("CODE");
    row.setStdCode("ORDER_STATUS_CREATED");
    row.setStdName("已创建");
    row.setStatus("UNKNOWN");
    row.setVersion(1);
    row.setSortOrder(1);
    row.setIsPreset(false);
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);

    assertThrows(IllegalStateException.class, () -> repository.findById(11L));
  }

  @Test
  void knownEnabledAndDisabledStatusesKeepTheirMeaning() {
    SemanticStandardPO row = new SemanticStandardPO();
    row.setId(13L);
    row.setProjectId(7L);
    row.setKind("TYPE");
    row.setStdCode("TYPE_STRING");
    row.setStdName("字符串");
    row.setStatus("ENABLED");
    row.setVersion(1);
    row.setSortOrder(1);
    row.setIsPreset(false);
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);

    assertEquals(io.yak.ops.business.semantic.api.StandardStatus.ENABLED,
        repository.findById(13L).orElseThrow().status());

    row.setStatus("DISABLED");
    assertEquals(io.yak.ops.business.semantic.api.StandardStatus.DISABLED,
        repository.findById(13L).orElseThrow().status());
  }

  @Test
  void missingStoredStatusIsNotSilentlyPromotedToEnabled() {
    SemanticStandardPO row = new SemanticStandardPO();
    row.setId(12L);
    row.setProjectId(7L);
    row.setKind("TYPE");
    row.setStdCode("TYPE_STRING");
    row.setStdName("字符串");
    row.setStatus(null);
    row.setVersion(1);
    row.setSortOrder(1);
    row.setIsPreset(false);
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);

    assertThrows(IllegalStateException.class, () -> repository.findById(12L));
  }
}
