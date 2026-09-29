package io.yak.ops.business.analysis.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.analysis.dao.model.AnalysisPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnalysisMapper extends BaseMapper<AnalysisPO> {
}
