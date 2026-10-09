package io.yak.ops.business.modeling.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.modeling.dao.mapper.ModelingColumnMappingMapper;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.core.project.CurrentProject;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** B-04: mapping edits must persist cleared nullable refs and never claim a zero-row update succeeded. */
class MappingRepositoryAdapterTest {

  private final ModelingColumnMappingMapper mapper = mock(ModelingColumnMappingMapper.class);
  private final CurrentProject project = mock(CurrentProject.class);
  private MappingRepositoryAdapter adapter;

  @BeforeEach
  void setup() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), ModelingColumnMappingPO.class);
    when(project.requireProjectId()).thenReturn(42L);
    adapter = new MappingRepositoryAdapter(mapper, project);
  }

  private static ModelingColumnMappingPO request() {
    ModelingColumnMappingPO po = new ModelingColumnMappingPO();
    po.setModelId(9L);
    po.setTargetColumn("amount");
    po.setSourceDatasourceId(3L);
    po.setSourceTable("orders");
    po.setSourceColumn("amount");
    po.setSourceDatabase(null);
    po.setTransformExpr(null);
    po.setStdProcessFieldId(null);
    return po;
  }

  private void existingRow() {
    ModelingColumnMappingPO persisted = new ModelingColumnMappingPO();
    persisted.setId(71L);
    persisted.setProjectId(42L);
    persisted.setModelId(9L);
    persisted.setTargetColumn("amount");
    persisted.setTransformExpr("OLD_EXPR");
    persisted.setStdProcessFieldId(55L);
    when(mapper.selectOne(any())).thenReturn(persisted);
  }

  @Test
  void clearsOldExpressionAndSemanticReferenceInsideProjectAndTargetScope() {
    existingRow();
    when(mapper.update(isNull(), any())).thenReturn(1);

    ModelingColumnMappingPO saved = adapter.upsert(request(), "editor");
    assertThat(saved.getId()).isEqualTo(71L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaUpdateWrapper<ModelingColumnMappingPO>> captured =
        ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    verify(mapper).update(isNull(), captured.capture());
    LambdaUpdateWrapper<ModelingColumnMappingPO> update = captured.getValue();
    assertThat(update.getSqlSet())
        .contains("source_database", "transform_expr", "std_process_field_id")
        .doesNotContain("created_by =", "project_id =");
    assertThat(update.getParamNameValuePairs().values()).containsNull();
    assertThat(update.getSqlSegment())
        .contains("project_id", "model_id", "target_column", "id");
  }

  @Test
  void aZeroRowConditionalWriteMustFailClosed() {
    existingRow();
    when(mapper.update(isNull(), any())).thenReturn(0);

    assertThatThrownBy(() -> adapter.upsert(request(), "editor"))
        .isInstanceOf(ModelingException.class)
        .satisfies(ex -> assertThat(((ModelingException) ex).getErrorCode())
            .isEqualTo(ModelingErrorCode.UPDATE_FAILED));
  }
}
