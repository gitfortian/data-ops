package io.yak.ops.business.sync.realtime.repository;

import java.util.List;

/** Global infrastructure boundary that discovers durable Project-owned reconciliation identities. */
public interface RealtimeReconcileDispatchStore {

  List<ProjectDeploymentRef> findCandidates();

  record ProjectDeploymentRef(long projectId, long definitionId, long deploymentId) {
    public ProjectDeploymentRef {
      if (projectId <= 0L) throw new IllegalArgumentException("ProjectId 必须大于 0");
      if (definitionId <= 0L) throw new IllegalArgumentException("DefinitionId 必须大于 0");
      if (deploymentId <= 0L) throw new IllegalArgumentException("DeploymentId 必须大于 0");
    }
  }
}
