package io.yak.ops.business.dashboard.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dashboard.dao.model.DashboardWidgetPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DashboardWidgetMapper extends BaseMapper<DashboardWidgetPO> {
}
