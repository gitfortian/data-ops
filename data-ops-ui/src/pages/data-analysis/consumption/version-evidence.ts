import type { ObservedVersion } from '@/services/consumption';

/** Never replace an executed version with the currently published product version. */
export const formatObservedVersion = (usage: ObservedVersion): string => {
  const version = usage.sourceVersion;
  const label = version.displayVersion ? `${version.displayVersion} · ` : '';
  return `${label}ID ${version.identity} · ${usage.successfulUsageCount} 次成功消费`;
};
