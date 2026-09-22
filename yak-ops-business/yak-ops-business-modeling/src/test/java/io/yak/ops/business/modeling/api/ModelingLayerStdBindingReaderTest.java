package io.yak.ops.business.modeling.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.semantic.api.LayerStdBindingReader.StdBindingStats;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelColumnMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.common.bean.po.modeling.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** M2-5 定标观察期:按 layer_code 聚合落标计数;未挂分层模型的字段不计入。 */
class ModelingLayerStdBindingReaderTest {

  private ModelingModelMapper modelMapper;
  private ModelingModelColumnMapper columnMapper;
  private ModelingLayerStdBindingReader reader;

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), ModelingModelPO.class);
    modelMapper = Mockito.mock(ModelingModelMapper.class);
    columnMapper = Mockito.mock(ModelingModelColumnMapper.class);
    CurrentProject currentProject = Mockito.mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(1L);
    reader = new ModelingLayerStdBindingReader(modelMapper, columnMapper, currentProject);
  }

  @Test
  void aggregatesBoundAndTotalPerLayer() {
    when(modelMapper.selectList(any()))
        .thenReturn(List.of(model(10L, "DWD"), model(11L, "DWD"), model(20L, "ODS")));
    when(columnMapper.selectMaps(any()))
        .thenReturn(
            List.of(
                row(10L, 5, 5),
                row(11L, 4, 2),
                row(20L, 8, 0),
                row(99L, 100, 0))); // 已删除/未挂分层模型的字段不计入

    Map<String, StdBindingStats> stats = reader.bindingStatsByLayer();

    assertEquals(new StdBindingStats(9, 7), stats.get("DWD"));
    assertEquals(new StdBindingStats(8, 0), stats.get("ODS"));
    assertEquals(2, stats.size());
  }

  @Test
  void noLayeredModelsReturnsEmpty() {
    when(modelMapper.selectList(any())).thenReturn(List.of());

    assertTrue(reader.bindingStatsByLayer().isEmpty());
  }

  private static ModelingModelPO model(long id, String layerCode) {
    ModelingModelPO po = new ModelingModelPO();
    po.setId(id);
    po.setLayerCode(layerCode);
    return po;
  }

  private static Map<String, Object> row(long modelId, long total, long bound) {
    Map<String, Object> row = new HashMap<>();
    row.put("model_id", modelId);
    row.put("column_total", total);
    row.put("std_bound_columns", bound);
    return row;
  }
}
