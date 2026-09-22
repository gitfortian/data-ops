package io.yak.ops.business.dataset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dataset.dao.model.DatasetQueryPerformancePO;
import java.sql.Timestamp;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DatasetQueryPerformanceMapper extends BaseMapper<DatasetQueryPerformancePO> {

  @Delete("DELETE FROM yak_dataset_query_performance "
      + "WHERE started_at < #{cutoff} ORDER BY id LIMIT #{limit}")
  int deleteBefore(@Param("cutoff") Timestamp cutoff, @Param("limit") int limit);
}
