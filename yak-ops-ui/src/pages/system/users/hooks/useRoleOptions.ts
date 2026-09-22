import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  listRoles,
  type RoleBrief,
} from '@/services/security/users';

import type { RoleOption } from '../types';

export function useRoleOptions(): RoleOption[] {
  const [roles, setRoles] = useState<RoleBrief[]>([]);

  const loadRoles = useCallback(async () => {
    try {
      const values = await listRoles();
      setRoles(Array.isArray(values) ? values : []);
    } catch {
      setRoles([]);
    }
  }, []);

  useEffect(() => {
    void loadRoles();
  }, [loadRoles]);

  return useMemo(
    () =>
      roles.map((role) => ({
        value: Number(role.id),
        label: role.roleName,
      })),
    [roles],
  );
}
