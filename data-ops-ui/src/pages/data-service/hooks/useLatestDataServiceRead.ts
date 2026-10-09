import { useCallback, useEffect, useRef } from 'react';

/** Accept only the newest page-scoped HTTP result, including failure and loading. */
export function useLatestDataServiceRead() {
  const version = useRef(0);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; version.current += 1; };
  }, []);
  return useCallback(() => {
    const current = ++version.current;
    return () => mounted.current && current === version.current;
  }, []);
}
