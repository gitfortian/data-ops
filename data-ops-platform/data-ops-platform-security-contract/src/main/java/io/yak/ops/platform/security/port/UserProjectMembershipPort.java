package io.yak.ops.platform.security.port;

import io.yak.framework.security.common.entity.UserProject;
import java.util.List;

/**
 * Narrow Security Platform port for Project membership operations.
 *
 * <p>Contains only stable IDs and Platform-owned relation entities;
 * the original UserProjectDao remains the compatibility facade for legacy DTO/PO queries.
 */
public interface UserProjectMembershipPort {
  List<Long> selectUserIdListByProjectId(Long projectId, int userType);

  List<Long> selectProjectIdListByUserIdList(List<Long> userIds);

  void insertBatch(List<UserProject> memberships);

  int deleteUserProject(List<UserProject> memberships);

  void deleteByProjectId(Long projectId);

  void deleteByUserId(Long userId);

  void deleteByProjectIdAndUserType(Long projectId, int userType);

  List<UserProject> selectByProjectIds(List<Long> projectIds);
  /** Criteria-only query; DTO conversion belongs exclusively to the legacy adapter. */
  List<UserProject> selectMembershipsByCriteria(UserProjectCriteria criteria);
}

