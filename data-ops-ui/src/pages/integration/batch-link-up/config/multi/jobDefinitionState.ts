// src/pages/sync/batch/multi/jobDefinitionState.ts

export type EditorSyncState = "UNPUBLISHED" | "SYNCED" | "DIRTY";
export type ReleaseState = "PUBLISHED" | "ONLINE" | "OFFLINE";

/** 发布态判定：PUBLISHED 为收敛后值，ONLINE 兼容存量。 */
export const isReleasedOnlineState = (state?: string | null) =>
  state === "PUBLISHED" || state === "ONLINE";

export type JobDefinitionState = {
  editorSyncState: EditorSyncState;
  releaseState: ReleaseState;
  jobVersion?: number | null;
  contentVersion?: number | null;
};

const readEnumCode = (value: any): string | undefined => {
  if (!value) return undefined;
  if (typeof value === "string") return value;
  return value.code || value.name || value.value;
};

const normalizeEditorSyncState = (value: any): EditorSyncState => {
  const code = readEnumCode(value);

  if (code === "UNPUBLISHED" || code === "SYNCED" || code === "DIRTY") {
    return code;
  }

  return "UNPUBLISHED";
};

const normalizeReleaseState = (value: any): ReleaseState => {
  const code = readEnumCode(value);

  if (code === "ONLINE") {
    return "PUBLISHED";
  }
  if (code === "PUBLISHED" || code === "OFFLINE") {
    return code;
  }

  return "OFFLINE";
};

export const normalizeJobDefinitionState = (
  state?: Partial<JobDefinitionState> | any
): JobDefinitionState => {
  return {
    editorSyncState: normalizeEditorSyncState(state?.editorSyncState),
    releaseState: normalizeReleaseState(state?.releaseState),
    jobVersion: state?.jobVersion ?? null,
    contentVersion: state?.contentVersion ?? null,
  };
};

export const buildUnpublishedJobDefinitionState = (): JobDefinitionState => {
  return {
    editorSyncState: "UNPUBLISHED",
    releaseState: "OFFLINE",
    jobVersion: null,
    contentVersion: null,
  };
};

export const markJobDefinitionDirty = (
  state?: Partial<JobDefinitionState> | any
): JobDefinitionState => {
  const current = normalizeJobDefinitionState(state);

  if (current.editorSyncState === "UNPUBLISHED") {
    return current;
  }

  return {
    ...current,
    editorSyncState: "DIRTY",
  };
};

export const markJobDefinitionSynced = (
  state?: Partial<JobDefinitionState> | any
): JobDefinitionState => {
  const current = normalizeJobDefinitionState(state);

  return {
    ...current,
    editorSyncState: "SYNCED",
  };
};

export const canRunByDefinitionState = (
  state?: Partial<JobDefinitionState> | any
): boolean => {
  const current = normalizeJobDefinitionState(state);

  return (
    isReleasedOnlineState(current.releaseState) &&
    current.editorSyncState === "SYNCED"
  );
};

export const getRunDisabledReasonByState = (
  state?: Partial<JobDefinitionState> | any
): string | undefined => {
  const current = normalizeJobDefinitionState(state);

  if (current.editorSyncState === "UNPUBLISHED") {
    return "任务还未发布，请先发布后再运行";
  }

  if (current.editorSyncState === "DIRTY") {
    return "当前脚本有未发布修改，请先发布后再运行";
  }

  if (!isReleasedOnlineState(current.releaseState)) {
    return "任务未上线，请先上线后再运行";
  }

  return undefined;
};

export const getEditorSyncStateText = (state?: EditorSyncState) => {
  switch (state) {
    case "SYNCED":
      return "已同步";
    case "DIRTY":
      return "已修改未发布";
    case "UNPUBLISHED":
    default:
      return "未发布";
  }
};

export const getReleaseStateText = (state?: ReleaseState) => {
  return isReleasedOnlineState(state) ? "已上线" : "已下线";
};