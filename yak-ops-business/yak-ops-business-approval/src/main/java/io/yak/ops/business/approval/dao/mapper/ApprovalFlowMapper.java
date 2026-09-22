package io.yak.ops.business.approval.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.approval.ApprovalFlowPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprovalFlowMapper extends BaseMapper<ApprovalFlowPO> {}
