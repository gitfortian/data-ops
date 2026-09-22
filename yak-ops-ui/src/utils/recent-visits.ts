export interface RecentVisit {
  path: string;
  title: string;
  visitedAt: number;
}

export const RECENT_VISITS_CHANGED_EVENT = 'yak-recent-visits-changed';

const STORAGE_KEY = 'yak-ops:recent-visits';
const MAX_RECENT_VISITS = 8;

export const readRecentVisits = (): RecentVisit[] => {
  if (typeof window === 'undefined') return [];
  try {
    const value = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '[]');
    if (!Array.isArray(value)) return [];
    return value.filter(
      (item): item is RecentVisit =>
        typeof item?.path === 'string'
        && typeof item?.title === 'string'
        && typeof item?.visitedAt === 'number',
    );
  } catch {
    return [];
  }
};

export const recordRecentVisit = (visit: Omit<RecentVisit, 'visitedAt'>) => {
  if (typeof window === 'undefined') return;
  const next = [
    { ...visit, visitedAt: Date.now() },
    ...readRecentVisits().filter((item) => item.path !== visit.path),
  ].slice(0, MAX_RECENT_VISITS);
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  window.dispatchEvent(new CustomEvent(RECENT_VISITS_CHANGED_EVENT));
};
