/** Classify only explicit read failures. Unknown provider/transport failures must not look like absent assets. */
export type AssetReadFailure = 'NOT_FOUND' | 'FORBIDDEN' | 'UNAVAILABLE';

export const classifyAssetReadFailure = (error: unknown): AssetReadFailure => {
  if (!error || typeof error !== 'object') return 'UNAVAILABLE';

  const value = error as { code?: unknown; response?: { status?: unknown; code?: unknown } };
  const status = value.response?.status;
  const businessCode = value.code ?? value.response?.code;

  // Asset's owning domain uses 48001 for absent or inaccessible records in the project.
  if (status === 403 || businessCode === 403) return 'FORBIDDEN';
  if (status === 404 || businessCode === 48001) return 'NOT_FOUND';
  return 'UNAVAILABLE';
};

/** List reads must not map a missing/project error to a successful empty page. */
export type AssetListReadFailure = 'FORBIDDEN' | 'UNAVAILABLE';

export const classifyAssetListFailure = (error: unknown): AssetListReadFailure =>
  classifyAssetReadFailure(error) === 'FORBIDDEN' ? 'FORBIDDEN' : 'UNAVAILABLE';
