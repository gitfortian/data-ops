package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.dao.RealtimeJobDao;
import org.springframework.stereotype.Repository;

@Repository
public class RealtimeReconcileDispatchStoreAdapter implements RealtimeReconcileDispatchStore {

  private final RealtimeJobDao dao;

  public RealtimeReconcileDispatchStoreAdapter(RealtimeJobDao dao) {
    this.dao = dao;
  }

  @Override
  public java.util.List<ProjectDeploymentRef> findCandidates() {
    return dao.findReconcileCandidatesForDispatch().stream()
        .map(ref -> new ProjectDeploymentRef(ref.projectId(), ref.definitionId(), ref.deploymentId()))
        .toList();
  }
}
