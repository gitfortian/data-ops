package io.yak.ops.business.sync.realtime.dao.mapper;

import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDeploymentPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RealtimeJobCommandMapper {
  RealtimeJobDefinitionPO lockDefinitionByProject(
      @Param("id") long id, @Param("projectId") long projectId);

  /** Explicit cross-Project dispatcher scan; callers must restore Project before business IO. */
  List<RealtimeJobDeploymentPO> reconcileExecutionsForDispatch();

  List<RealtimeJobDeploymentPO> reconcileExecutionsByProject(
      @Param("projectId") long projectId);

  int tryAcquireLease(@Param("owner") String owner, @Param("leaseSeconds") int leaseSeconds);

  int reconcileDeploymentByProject(
      @Param("deploymentId") long deploymentId,
      @Param("projectId") long projectId,
      @Param("observedState") String observedState,
      @Param("deploymentState") String deploymentState,
      @Param("engineJobId") String engineJobId,
      @Param("error") String error);
}
