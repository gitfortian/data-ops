package io.yak.ops.platform.security.port;

import io.yak.framework.security.common.entity.UserRole;
import java.util.List;

/**
 * Narrow Security Platform port for role assignment operations.
 * Database-specific UserRolePO reads remain in the legacy DAO facade.
 */
public interface UserRoleAssignmentPort {
  List<Long> selectUserIdListByRoleId(Long roleId);

  List<Long> selectRoleIdListByUserId(Long userId);

  void insertBatch(List<UserRole> assignments);

  int deleteByUserIdOrRoleId(Long userId, Long roleId);

  int selectCountByRoleId(Long roleId);
  /** Role assignment reads projected to Platform-owned models, not database PO. */
  List<UserRole> selectAssignmentsByRoleIds(List<Long> roleIds);

  List<UserRole> selectAssignmentsByUserIds(List<Long> userIds);
}

