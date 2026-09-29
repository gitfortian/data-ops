import HttpUtils from '@/utils/HttpUtils';

import type {
  CreateDevelopmentDirectoryPayload,
  CreateDevelopmentNodePayload,
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentReleaseDetail,
  DevelopmentReleasePage,
  DevelopmentReleaseQuery,
  DevelopmentReleaseSummary,
  DevelopmentResourceNode,
  DevelopmentStandardCheck,
  DevelopmentSqlLineagePreview,
  DevelopmentSqlLineagePreviewRequest,
  DevelopmentTaskDefinition,
  DevelopmentTaskDraft,
  DevelopmentTaskExecutionDetail,
  DevelopmentTaskExecutionPage,
  DevelopmentTaskExecutionQuery,
  DevelopmentTaskExecutionSubmission,
  DevelopmentTaskRevision,
  DevelopmentTaskRevisionSummary,
  SaveDevelopmentTaskDraftPayload,
  YakEditorSettings,
} from './types';

const DATA_DEVELOPMENT_API = '/api/v1/data-development';
const DIRECTORY_API = `${DATA_DEVELOPMENT_API}/directories`;
const NODE_API = `${DATA_DEVELOPMENT_API}/nodes`;
const EXECUTION_API = `${DATA_DEVELOPMENT_API}/executions`;
const RELEASE_API = `${DATA_DEVELOPMENT_API}/releases`;
const EDITOR_SETTINGS_API = `${DATA_DEVELOPMENT_API}/editor-settings`;

const resourcePath = (prefix: string, id: DevelopmentId) =>
  `${prefix}/${encodeURIComponent(id)}`;

const queryString = (query: object) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    params.set(key, String(value));
  });
  const value = params.toString();
  return value ? `?${value}` : '';
};

export const listDevelopmentDirectories = (): Promise<
  DevelopmentDirectory[]
> => HttpUtils.getData<DevelopmentDirectory[]>(DIRECTORY_API);

export const createDevelopmentDirectory = (
  payload: CreateDevelopmentDirectoryPayload,
): Promise<DevelopmentDirectory> =>
  HttpUtils.postData<DevelopmentDirectory>(DIRECTORY_API, payload);

export const renameDevelopmentDirectory = (
  id: DevelopmentId,
  name: string,
): Promise<DevelopmentDirectory> =>
  HttpUtils.putData<DevelopmentDirectory>(
    `${resourcePath(DIRECTORY_API, id)}/name`,
    { name },
  );

export const moveDevelopmentDirectory = (
  id: DevelopmentId,
  parentId: DevelopmentId | null,
): Promise<DevelopmentDirectory> =>
  HttpUtils.putData<DevelopmentDirectory>(
    `${resourcePath(DIRECTORY_API, id)}/parent`,
    { parentId },
  );

export const deleteDevelopmentDirectory = async (
  id: DevelopmentId,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(resourcePath(DIRECTORY_API, id));
};

export const listDevelopmentNodes = (): Promise<DevelopmentResourceNode[]> =>
  HttpUtils.getData<DevelopmentResourceNode[]>(NODE_API);

export const createDevelopmentNode = (
  payload: CreateDevelopmentNodePayload,
): Promise<DevelopmentResourceNode> =>
  HttpUtils.postData<DevelopmentResourceNode>(NODE_API, payload);

export const renameDevelopmentNode = (
  id: DevelopmentId,
  name: string,
): Promise<DevelopmentResourceNode> =>
  HttpUtils.putData<DevelopmentResourceNode>(
    `${resourcePath(NODE_API, id)}/name`,
    { name },
  );

export const moveDevelopmentNode = (
  id: DevelopmentId,
  directoryId: DevelopmentId | null,
): Promise<DevelopmentResourceNode> =>
  HttpUtils.putData<DevelopmentResourceNode>(
    `${resourcePath(NODE_API, id)}/directory`,
    { directoryId },
  );

export const deleteDevelopmentNode = async (
  id: DevelopmentId,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(resourcePath(NODE_API, id));
};

export const getDevelopmentTaskDraft = (
  nodeId: DevelopmentId,
): Promise<DevelopmentTaskDraft> =>
  HttpUtils.getData<DevelopmentTaskDraft>(
    `${resourcePath(NODE_API, nodeId)}/draft`,
  );

export const saveDevelopmentTaskDraft = (
  nodeId: DevelopmentId,
  payload: SaveDevelopmentTaskDraftPayload,
): Promise<DevelopmentTaskDraft> =>
  HttpUtils.putData<DevelopmentTaskDraft>(
    `${resourcePath(NODE_API, nodeId)}/draft`,
    payload,
  );

/** Submit current editor definition and return immediately with durable execution identity. */
export const runDevelopmentTask = (
  nodeId: DevelopmentId,
  payload: DevelopmentTaskDefinition,
): Promise<DevelopmentTaskExecutionSubmission> =>
  HttpUtils.postData<DevelopmentTaskExecutionSubmission>(
    `${resourcePath(NODE_API, nodeId)}/run`,
    payload,
  );

/** Parse current SQL content without saving, publishing or registering lineage. */
export const previewDevelopmentSqlLineage = (
  nodeId: DevelopmentId,
  payload: DevelopmentSqlLineagePreviewRequest,
): Promise<DevelopmentSqlLineagePreview> =>
  HttpUtils.postData<DevelopmentSqlLineagePreview>(
    `${resourcePath(NODE_API, nodeId)}/lineage/preview`,
    payload,
  );

/** Check current SQL output fields against naming standards without saving or publishing. */
export const checkDevelopmentSqlStandards = (
  nodeId: DevelopmentId,
  payload: DevelopmentTaskDefinition,
): Promise<DevelopmentStandardCheck> =>
  HttpUtils.postData<DevelopmentStandardCheck>(
    `${resourcePath(NODE_API, nodeId)}/standard-check`,
    payload,
  );

export const listDevelopmentTaskExecutions = (
  query: DevelopmentTaskExecutionQuery,
): Promise<DevelopmentTaskExecutionPage> =>
  HttpUtils.getData<DevelopmentTaskExecutionPage>(
    `${EXECUTION_API}${queryString(query)}`,
  );

export const getDevelopmentTaskExecution = (
  id: DevelopmentId,
): Promise<DevelopmentTaskExecutionDetail> =>
  HttpUtils.getData<DevelopmentTaskExecutionDetail>(
    resourcePath(EXECUTION_API, id),
  );

export const getActiveDevelopmentTaskExecution = (
  nodeId: DevelopmentId,
): Promise<DevelopmentTaskExecutionDetail | null> =>
  HttpUtils.getData<DevelopmentTaskExecutionDetail | null>(
    `${EXECUTION_API}/active?nodeId=${encodeURIComponent(nodeId)}`,
  );

export const cancelDevelopmentTaskExecution = (
  id: DevelopmentId,
): Promise<DevelopmentTaskExecutionDetail> =>
  HttpUtils.postData<DevelopmentTaskExecutionDetail>(
    `${resourcePath(EXECUTION_API, id)}/cancel`,
    {},
  );

export const retryDevelopmentTaskExecution = (
  id: DevelopmentId,
): Promise<DevelopmentTaskExecutionSubmission> =>
  HttpUtils.postData<DevelopmentTaskExecutionSubmission>(
    `${resourcePath(EXECUTION_API, id)}/retry`,
    {},
  );

export const listDevelopmentReleases = (
  query: DevelopmentReleaseQuery,
): Promise<DevelopmentReleasePage> =>
  HttpUtils.getData<DevelopmentReleasePage>(
    `${RELEASE_API}${queryString(query)}`,
  );

export const getDevelopmentRelease = (
  assetId: DevelopmentId,
): Promise<DevelopmentReleaseDetail> =>
  HttpUtils.getData<DevelopmentReleaseDetail>(
    resourcePath(RELEASE_API, assetId),
  );

export const offlineDevelopmentRelease = (
  assetId: DevelopmentId,
): Promise<DevelopmentReleaseSummary> =>
  HttpUtils.postData<DevelopmentReleaseSummary>(
    `${resourcePath(RELEASE_API, assetId)}/offline`,
    {},
  );

export const onlineDevelopmentRelease = (
  assetId: DevelopmentId,
): Promise<DevelopmentReleaseSummary> =>
  HttpUtils.postData<DevelopmentReleaseSummary>(
    `${resourcePath(RELEASE_API, assetId)}/online`,
    {},
  );

export const activateDevelopmentReleaseRevision = (
  assetId: DevelopmentId,
  revisionNo: number,
): Promise<DevelopmentReleaseSummary> =>
  HttpUtils.postData<DevelopmentReleaseSummary>(
    `${resourcePath(RELEASE_API, assetId)}/activate/${revisionNo}`,
    {},
  );

export const publishDevelopmentTask = (
  nodeId: DevelopmentId,
  draftRevision: number,
): Promise<DevelopmentTaskRevision> =>
  HttpUtils.postData<DevelopmentTaskRevision>(
    `${resourcePath(NODE_API, nodeId)}/publish`,
    { draftRevision },
  );

export const listDevelopmentTaskRevisions = (
  nodeId: DevelopmentId,
): Promise<DevelopmentTaskRevisionSummary[]> =>
  HttpUtils.getData<DevelopmentTaskRevisionSummary[]>(
    `${resourcePath(NODE_API, nodeId)}/revisions`,
  );

export const getDevelopmentTaskRevision = (
  nodeId: DevelopmentId,
  revisionNo: number,
): Promise<DevelopmentTaskRevision> =>
  HttpUtils.getData<DevelopmentTaskRevision>(
    `${resourcePath(NODE_API, nodeId)}/revisions/${revisionNo}`,
  );

export const getDevelopmentEditorSettings = (): Promise<YakEditorSettings> =>
  HttpUtils.getData<YakEditorSettings>(EDITOR_SETTINGS_API);

export const saveDevelopmentEditorSettings = (
  settings: YakEditorSettings,
): Promise<YakEditorSettings> =>
  HttpUtils.putData<YakEditorSettings>(EDITOR_SETTINGS_API, settings);
