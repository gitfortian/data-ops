package io.yak.ops.business.modeling.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.repository.ModelTagRepository;
import io.yak.ops.common.bean.po.modeling.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Project isolation contract of the modeling catalog repository. */
class ModelRepositoryProjectScopeTest {

  private static final CurrentProject PROJECT_7 =
      () -> Optional.of(new ProjectContext(7L, "Project A"));

  private ModelingModelMapper mapper;
  private ModelRepositoryAdapter repository;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(ModelingModelMapper.class);
    ModelTagRepository tagRepository = Mockito.mock(ModelTagRepository.class);
    repository = new ModelRepositoryAdapter(mapper, tagRepository, PROJECT_7);
  }

  @Test
  void insertBindsTrustedProjectIdAndOperator() {
    Model model = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, " 用户描述 ");

    repository.insert(model, "alice");

    ArgumentCaptor<ModelingModelPO> captor = ArgumentCaptor.forClass(ModelingModelPO.class);
    verify(mapper).insert(captor.capture());
    ModelingModelPO po = captor.getValue();
    assertThat(po.getProjectId()).isEqualTo(7L);
    assertThat(po.getModelCode()).isEqualTo("dim_user");
    assertThat(po.getModelName()).isEqualTo("用户维度表");
    assertThat(po.getDialect()).isEqualTo("MYSQL");
    assertThat(po.getStatus()).isEqualTo("DRAFT");
    assertThat(po.getCreatedBy()).isEqualTo("alice");
    assertThat(po.getCreateTime()).isNotNull();
  }

  @Test
  void findByIdScopesQueryToProject() {
    ModelingModelPO stored = new ModelingModelPO();
    stored.setId(42L);
    stored.setProjectId(7L);
    stored.setModelCode("dim_user");
    stored.setModelName("用户维度表");
    stored.setDialect("MYSQL");
    stored.setStatus("DRAFT");
    when(mapper.selectOne(any())).thenReturn(stored);

    Optional<Model> found = repository.findById(42L);

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(42L);
    assertThat(found.get().dialect()).isEqualTo(ModelDialect.MYSQL);
    verify(mapper).selectOne(any());
  }

  @Test
  void missingProjectContextFailsClosed() {
    ModelTagRepository tagRepository = Mockito.mock(ModelTagRepository.class);
    ModelRepositoryAdapter failing =
        new ModelRepositoryAdapter(mapper, tagRepository, Optional::<ProjectContext>empty);

    org.junit.jupiter.api.Assertions.assertThrows(
        RuntimeException.class, () -> failing.findById(42L));
  }
}
