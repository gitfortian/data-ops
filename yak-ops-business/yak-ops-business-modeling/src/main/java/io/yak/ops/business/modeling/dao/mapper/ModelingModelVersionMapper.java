package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.modeling.ModelingModelVersionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis mapper for the modeling model version table. */
@Mapper
public interface ModelingModelVersionMapper extends BaseMapper<ModelingModelVersionPO> {

  /** 版本号由 MAX+1 原子推导，不依赖计数（删除历史行也不回退）。 */
  @Select(
      "SELECT COALESCE(MAX(version_no), 0) + 1 FROM yak_modeling_model_version"
          + " WHERE project_id = #{projectId} AND model_id = #{modelId}")
  int nextVersionNo(@Param("projectId") Long projectId, @Param("modelId") Long modelId);
}
