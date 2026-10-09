import { useCallback, useEffect, useRef } from 'react';

/**
 * Ignore delayed reads after filters change or a Project-scoped page unmounts.
 * Source/project isolation is still enforced by the server.
 */
export const useLatestAssetListRequest = () => {
  const sequence = useRef(0);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      sequence.current += 1;
    };
  }, []);

  return useCallback(() => {
    const request = ++sequence.current;
    return () => mounted.current && sequence.current === request;
  }, []);
};
