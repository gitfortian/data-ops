package io.yak.ops.business.metadata.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.metadata.MdLabelPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MdLabelMapper extends BaseMapper<MdLabelPO> {}
