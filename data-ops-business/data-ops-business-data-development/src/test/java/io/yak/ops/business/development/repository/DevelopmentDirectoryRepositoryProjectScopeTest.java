package io.yak.ops.business.development.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.dao.mapper.DevelopmentDirectoryMapper;
import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.common.bean.po.development.DevelopmentDirectoryPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DevelopmentDirectoryRepositoryProjectScopeTest {

  @Mock private DevelopmentDirectoryMapper mapper;

  @Test
  void insertUsesTrustedCurrentProject() {
    when(mapper.insert(any(DevelopmentDirectoryPO.class)))
        .thenAnswer(
            invocation -> {
              DevelopmentDirectoryPO po = invocation.getArgument(0);
              po.setId(11L);
              return 1;
            });
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    DevelopmentDirectoryRepositoryAdapter repository =
        new DevelopmentDirectoryRepositoryAdapter(mapper, currentProject);

    DevelopmentDirectory created = repository.insert(null, "jobs");

    assertThat(created.id()).isEqualTo(11L);
    ArgumentCaptor<DevelopmentDirectoryPO> captor =
        ArgumentCaptor.forClass(DevelopmentDirectoryPO.class);
    org.mockito.Mockito.verify(mapper).insert(captor.capture());
    assertThat(captor.getValue().getProjectId()).isEqualTo(7L);
  }
}
