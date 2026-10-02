package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;

/**
 * Workflow versions persist Project directly because RUNTIME versions may not have a parent Workflow.
 *
 * <p>所有读写都通过 BaseMapper + Wrapper 表达，无 XML。
 */
public interface WorkflowVersionMapper extends BaseMapper<WorkflowVersionPO> {}
