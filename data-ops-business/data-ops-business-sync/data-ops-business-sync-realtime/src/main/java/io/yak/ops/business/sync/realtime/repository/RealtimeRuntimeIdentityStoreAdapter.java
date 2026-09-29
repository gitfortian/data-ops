package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.dao.RealtimeJobDao;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RealtimeRuntimeIdentityStoreAdapter implements RealtimeRuntimeIdentityStore {

  private final RealtimeJobDao dao;

  public RealtimeRuntimeIdentityStoreAdapter(RealtimeJobDao dao) {
    this.dao = dao;
  }

  @Override
  public void bind(String idempotencyKey, String runtimeJobName) {
    if (dao.bindRuntimeIdentity(idempotencyKey, runtimeJobName) != 1) {
      throw new IllegalStateException("无法绑定实时同步 runtime job identity，部署状态可能已变化");
    }
  }

  @Override
  public Optional<String> findByDeploymentId(long deploymentId) {
    return dao.runtimeJobName(deploymentId);
  }
}
