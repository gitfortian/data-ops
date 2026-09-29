package io.yak.ops.business.job.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.job.dao.SystemEnvVarDao;
import io.yak.ops.business.job.dao.mapper.SystemEnvVarMapper;
import io.yak.ops.common.bean.po.job.SystemEnvVarPO;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的系统环境变量数据访问实现。 */
@Repository
public class SystemEnvVarDaoImpl implements SystemEnvVarDao {

  private final SystemEnvVarMapper mapper;

  public SystemEnvVarDaoImpl(SystemEnvVarMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<SystemEnvVarPO> selectAll() {
    return mapper.selectList(
        Wrappers.<SystemEnvVarPO>lambdaQuery()
            .orderByAsc(SystemEnvVarPO::getVarKey));
  }

  @Override
  public SystemEnvVarPO selectByKey(String varKey) {
    return mapper.selectOne(
        Wrappers.<SystemEnvVarPO>lambdaQuery()
            .eq(SystemEnvVarPO::getVarKey, varKey));
  }

  @Override
  public int insert(SystemEnvVarPO po) {
    return mapper.insert(po);
  }

  @Override
  public int updateByKey(SystemEnvVarPO po) {
    return mapper.update(po,
        Wrappers.<SystemEnvVarPO>lambdaUpdate()
            .eq(SystemEnvVarPO::getVarKey, po.getVarKey()));
  }

  @Override
  public int deleteByKey(String varKey) {
    return mapper.delete(
        Wrappers.<SystemEnvVarPO>lambdaQuery()
            .eq(SystemEnvVarPO::getVarKey, varKey));
  }
}
