package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.dao.ComputeEnvironmentDao;
import io.yak.ops.business.sync.realtime.dao.model.ComputeEnvironmentPO;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.repository.support.RealtimeJsonCodec;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ComputeEnvironmentStoreAdapter implements ComputeEnvironmentStore {

  private final ComputeEnvironmentDao dao;
  private final RealtimeJsonCodec json;

  public ComputeEnvironmentStoreAdapter(ComputeEnvironmentDao dao, RealtimeJsonCodec json) {
    this.dao = dao;
    this.json = json;
  }

  @Override
  public List<ComputeEnvironment> list() {
    return dao.list().stream().map(this::map).toList();
  }

  @Override
  public Optional<ComputeEnvironment> find(long id) {
    return dao.find(id).map(this::map);
  }

  @Override
  public Optional<ComputeEnvironment> defaultEnvironment() {
    return dao.defaultEnvironment().map(this::map);
  }

  @Override
  public long insert(
      String name,
      String engineType,
      String deploymentMode,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean defaultEnvironment) {
    ComputeEnvironmentPO po = new ComputeEnvironmentPO();
    po.setName(name);
    po.setEngineType(engineType);
    po.setDeploymentMode(deploymentMode);
    po.setSubmitterType(submitterType);
    po.setConfigJson(json.write(config));
    po.setEnabled(enabled);
    po.setIsDefault(defaultEnvironment);
    return dao.insert(po);
  }

  @Override
  public void update(long id, String name, String submitterType, RuntimeConfig config, boolean enabled) {
    if (dao.update(id, name, submitterType, json.write(config), enabled) != 1) {
      throw new IllegalArgumentException("运行环境不存在：" + id);
    }
  }

  @Override
  public void setEnabled(long id, boolean enabled) {
    if (dao.setEnabled(id, enabled) != 1) {
      throw new IllegalArgumentException("运行环境不存在：" + id);
    }
  }

  @Override
  public void saveDiagnosis(long id, String status, String message, LocalDateTime checkedAt) {
    if (dao.saveDiagnosis(id, status, message, checkedAt) != 1) {
      throw new IllegalArgumentException("运行环境不存在：" + id);
    }
  }

  @Override
  public void clearDefault() {
    dao.clearDefault();
  }

  @Override
  public void setDefault(long id) {
    if (dao.setDefault(id) != 1) {
      throw new IllegalStateException("只有已启用的运行环境才能设为默认环境");
    }
  }

  @Override
  public void delete(long id) {
    if (dao.delete(id) != 1) {
      throw new IllegalStateException("默认运行环境不能删除，请先切换默认环境");
    }
  }

  @Override
  public boolean hasRuntimeEnvironmentReferences(long id) {
    return dao.hasRuntimeEnvironmentReferences(id);
  }

  private ComputeEnvironment map(ComputeEnvironmentPO po) {
    return new ComputeEnvironment(
        po.getId(),
        po.getName(),
        po.getEngineType(),
        po.getDeploymentMode(),
        po.getSubmitterType(),
        json.readRuntimeConfig(po.getConfigJson()),
        Boolean.TRUE.equals(po.getEnabled()),
        Boolean.TRUE.equals(po.getIsDefault()),
        po.getVersion() == null ? 0 : po.getVersion(),
        po.getCreateTime(),
        po.getUpdateTime(),
        po.getLastCheckStatus(),
        po.getLastCheckMessage(),
        po.getLastCheckTime());
  }
}
