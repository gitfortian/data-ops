package io.yak.ops.business.datasource.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.datasource.dao.model.SqlStatementExecutionAuditPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for statement-level SQL audit records. */
@Mapper
public interface SqlStatementExecutionAuditMapper extends BaseMapper<SqlStatementExecutionAuditPO> {}
