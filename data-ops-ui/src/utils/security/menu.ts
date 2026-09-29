const ROOT_PERMISSION = 'security:root';

/**
 * Checks one database-backed menu grant.
 *
 * An undefined menuCodes value keeps staggered frontend/backend deployments
 * compatible. Once the backend exposes menuCodes, protected navigation fails
 * closed when no stable menu code is declared or granted.
 */
export const hasMenuAccess = (
  menuCodes: readonly string[] | null | undefined,
  requiredMenuCode: string | null | undefined,
  permissionCodes?: readonly string[] | null,
  publicAccess = false,
): boolean => {
  if (publicAccess) {
    return true;
  }

  if (permissionCodes?.includes(ROOT_PERMISSION)) {
    return true;
  }

  if (!Array.isArray(menuCodes)) {
    return true;
  }

  if (!requiredMenuCode) {
    return false;
  }

  return menuCodes.includes(requiredMenuCode);
};
