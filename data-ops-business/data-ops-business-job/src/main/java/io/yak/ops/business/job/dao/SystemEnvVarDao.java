package io.yak.ops.business.job.dao;

import io.yak.ops.common.bean.po.job.SystemEnvVarPO;
import java.util.List;

/** 系统环境变量数据访问接口。 */
public interface SystemEnvVarDao {

  List<SystemEnvVarPO> selectAll();

  SystemEnvVarPO selectByKey(String varKey);

  int insert(SystemEnvVarPO po);

  int updateByKey(SystemEnvVarPO po);

  int deleteByKey(String varKey);
}
