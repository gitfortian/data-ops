import { useCallback, useEffect, useRef } from "react";

function useOperationState(resource: string | number | undefined) {
  const current = useRef({ resource, generation: 0, mounted: true });
  if (current.current.resource !== resource) {
    current.current.resource = resource;
    current.current.generation += 1;
  }
  useEffect(() => {
    current.current.mounted = true;
    return () => {
      current.current.mounted = false;
      current.current.generation += 1;
    };
  }, []);
  return current;
}

/** Independent operations may finish together, but never write into a different resource. */
export function useResourceScope(resource: string | number | undefined) {
  const current = useOperationState(resource);
  return useCallback(() => {
    const generation = current.current.generation;
    return () =>
      current.current.mounted &&
      current.current.resource === resource &&
      current.current.generation === generation;
  }, [resource]);
}

/** A response may update an editor only while its operation and resource are current. */
export function useLatestOperation(resource: string | number | undefined) {
  const current = useOperationState(resource);
  return useCallback(() => {
    const capturedResource = current.current.resource;
    const generation = ++current.current.generation;
    return () =>
      current.current.mounted &&
      current.current.resource === capturedResource &&
      current.current.generation === generation;
  }, []);
}
