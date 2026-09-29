package io.yak.ops.business.modeling.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.semantic.api.LayerUsageReader;
import io.yak.ops.common.bean.po.modeling.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * semantic 反向只读 SPI 实现(2026-09-16):统计项目内未被软删除的模型按 layer_code 分组计数,
 * 供分层列表展示与自定义分层删除阻断。modeling 依赖 semantic(单向),此处实现其接口即可。
 */
@Component
@RequiredArgsConstructor
public class ModelingLayerUsageReader implements LayerUsageReader {

  private final ModelingModelMapper modelMapper;
  private final CurrentProject currentProject;

  @Override
  public Map<String, Long> countModelsByLayer() {
    List<Map<String, Object>> rows =
        modelMapper.selectMaps(
            new QueryWrapper<ModelingModelPO>()
                .select("layer_code AS layer_code", "COUNT(*) AS model_count")
                .eq("project_id", currentProject.requireProjectId())
                .eq("deleted", false)
                .isNotNull("layer_code")
                .ne("layer_code", "")
                .groupBy("layer_code"));
    Map<String, Long> counts = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      Object code = row.get("layer_code");
      Object count = row.get("model_count");
      if (code != null && count instanceof Number number) {
        counts.put(code.toString(), number.longValue());
      }
    }
    return counts;
  }
}
