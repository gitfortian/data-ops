package io.yak.ops.business.dataservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiKeyPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataServiceApiKeyMapper extends BaseMapper<DataServiceApiKeyPO> {}
