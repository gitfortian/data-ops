import HttpUtils from '@/utils/HttpUtils';
import type { ApprovalInstance } from '@/services/approval/types';
import type {
  AssetLineageSummary,
  AssetOverviewData,
  AssetDetailView,
  AssetPageParams,
  AssetPageResult,
  AssetRecord,
  AssetSnapshotEditParams,
  AssetTagRecord,
  ChangeHandleStatus,
  ChangeRecord,
  DailyView,
  DirectoryUpsertParams,
  DirNode,
  ManualRegisterParams,
  PrecheckResult,
  ReconcileStatusRow,
  RecordPage,
  RuleDryRunResult,
  RuleRecord,
  RuleUpsertParams,
  TagUpsertParams,
} from './types';

const ASSET_API_PREFIX = '/api/v1/assets';

type RawPage<T> = {
  bizData?: T[];
  records?: T[];
  total?: number;
  pages?: number;
  pageNo?: number;
  pageSize?: number;
  pagination?: { pageNo?: number; pageSize?: number; total?: number; pages?: number };
};

const toPageResult = (raw: RawPage<AssetRecord>, pageSize: number): AssetPageResult =>
  toRecordPage(raw, pageSize);

const toRecordPage = <T>(raw: RawPage<T>, pageSize: number): RecordPage<T> => {
  const pagination = raw?.pagination ?? {};
  return {
    records: raw?.bizData ?? raw?.records ?? [],
    total: pagination.total ?? raw?.total ?? 0,
    pageNo: pagination.pageNo ?? raw?.pageNo ?? 1,
    pageSize: pagination.pageSize ?? raw?.pageSize ?? pageSize,
  };
};

/** Spring @ModelAttribute 绑定 List 用逗号分隔最稳(axios 默认会序列化成 key[])。 */
const flatQuery = (params: AssetPageParams): string => {
  const { assetTypes, layerCodes, statuses, grades, tagIds, ...rest } = params;
  const search = new URLSearchParams();
  const put = (key: string, value: unknown) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value));
  };
  Object.entries(rest).forEach(([key, value]) => put(key, value));
  const join = (list?: unknown[]) => (list && list.length > 0 ? list.join(',') : undefined);
  put('assetTypes', join(assetTypes));
  put('layerCodes', join(layerCodes));
  put('statuses', join(statuses));
  put('grades', join(grades));
  put('tagIds', join(tagIds));
  return search.toString();
};

// ---------- 台账 / 发现 ----------

export const pageAssets = async (params: AssetPageParams): Promise<AssetPageResult> => {
  const query = flatQuery(params);
  return toPageResult(
    await HttpUtils.getData<RawPage<AssetRecord>>(
      `${ASSET_API_PREFIX}${query ? `?${query}` : ''}`,
    ),
    params.pageSize ?? 20,
  );
};

export const getAssetDetail = (id: number) =>
  HttpUtils.getData<AssetDetailView>(`${ASSET_API_PREFIX}/${id}`);

/** 浏览上报(服务端同用户同资产 5 分钟去重;尽力而为,调用方静默 catch)。 */
export const reportAssetView = (id: number, entry: string) =>
  HttpUtils.postData<boolean>(`${ASSET_API_PREFIX}/${id}/view`, { entry }, {
    skipErrorHandler: true,
  });

export const getAssetViewTrend = (id: number) =>
  HttpUtils.getData<DailyView[]>(`${ASSET_API_PREFIX}/${id}/views`);

/** 跨域引用摘要(只读,供指标详情等消费侧);血缘/质量事实缺失时 available=false。 */
export const getAssetLineageSummary = (assetKey: string, depth?: number) => {
  const search = new URLSearchParams({ assetKey });
  if (depth) search.set('depth', String(depth));
  return HttpUtils.getData<AssetLineageSummary>(
    `${ASSET_API_PREFIX}/lineage-summary?${search.toString()}`,
  );
};

export const registerManualAsset = (params: ManualRegisterParams) =>
  HttpUtils.postData<AssetRecord>(ASSET_API_PREFIX, params);

export const updateAssetSnapshot = (id: number, params: AssetSnapshotEditParams) =>
  HttpUtils.putData<AssetRecord>(`${ASSET_API_PREFIX}/${id}`, params);

export const changeAssetOwner = (id: number, owner: string) =>
  HttpUtils.putData<AssetRecord>(`${ASSET_API_PREFIX}/${id}/owner`, { owner });

export const deleteAsset = (id: number) =>
  HttpUtils.deleteData<boolean>(`${ASSET_API_PREFIX}/${id}`);

// ---------- 上架状态机 ----------

export const precheckAssets = (assetIds: number[]) =>
  HttpUtils.postData<PrecheckResult>(`${ASSET_API_PREFIX}/precheck`, { assetIds });

export const publishAssets = (assetIds: number[], token: string, acceptRisk: boolean) =>
  HttpUtils.postData<number>(`${ASSET_API_PREFIX}/publish`, { assetIds, token, acceptRisk });

/** 发起上架审批(M2-5);同资产在途单唯一,批准即走 publish 链路。 */
export const submitAssetPublishApproval = (assetId: number) =>
  HttpUtils.postData<ApprovalInstance>(`${ASSET_API_PREFIX}/${assetId}/publish-approval`, {});

export const offlineAssets = (assetIds: number[], reason: string) =>
  HttpUtils.postData<number>(`${ASSET_API_PREFIX}/offline`, { assetIds, reason });

export const ignoreAssets = (assetIds: number[]) =>
  HttpUtils.postData<number>(`${ASSET_API_PREFIX}/ignore`, { assetIds });

export const batchMoveAssetsDirectory = (assetIds: number[], directoryId?: number | null) =>
  HttpUtils.postData<number>(`${ASSET_API_PREFIX}/batch/move-directory`, {
    assetIds,
    directoryId: directoryId ?? undefined,
  });

// ---------- 目录 / 标签 ----------

export const getDirectoryTree = () =>
  HttpUtils.getData<DirNode[]>(`${ASSET_API_PREFIX}/directories/tree`);

export const listAssetTags = (keyword?: string) =>
  HttpUtils.getData<AssetTagRecord[]>(
    `${ASSET_API_PREFIX}/tags${keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''}`,
  );

export const getAssetTags = (assetId: number) =>
  HttpUtils.getData<AssetTagRecord[]>(`${ASSET_API_PREFIX}/${assetId}/tags`);

export const attachAssetTags = (assetId: number, tagIds: number[]) =>
  HttpUtils.postData<number>(`${ASSET_API_PREFIX}/${assetId}/tags`, { tagIds });

export const detachAssetTag = (assetId: number, tagId: number) =>
  HttpUtils.deleteData<number>(`${ASSET_API_PREFIX}/${assetId}/tags/${tagId}`);

// ---------- 概览驾驶舱 ----------

export const getAssetOverview = () =>
  HttpUtils.getData<AssetOverviewData>(`${ASSET_API_PREFIX}/overview`);

// ---------- 盘点(对账 + 变更确认) ----------

/** 手动对账:异步受理,返回受理说明;互斥 48012。 */
export const triggerReconcile = (sourceTypes: string[] = []) =>
  HttpUtils.postData<string>(`${ASSET_API_PREFIX}/reconcile`, { sourceTypes });

export const getReconcileStatus = () =>
  HttpUtils.getData<ReconcileStatusRow[]>(`${ASSET_API_PREFIX}/reconcile/status`);

export const pageChanges = async (params: {
  handleStatus?: ChangeHandleStatus;
  pageNo?: number;
  pageSize?: number;
}): Promise<RecordPage<ChangeRecord>> => {
  const search = new URLSearchParams();
  if (params.handleStatus) search.set('handleStatus', params.handleStatus);
  search.set('pageNo', String(params.pageNo ?? 1));
  search.set('pageSize', String(params.pageSize ?? 20));
  return toRecordPage(
    await HttpUtils.getData<RawPage<ChangeRecord>>(
      `${ASSET_API_PREFIX}/changes?${search.toString()}`,
    ),
    params.pageSize ?? 20,
  );
};

/** 确认变更:快照新值覆盖台账展示字段;已处理 48014。 */
export const confirmChange = (id: number) =>
  HttpUtils.postData<ChangeRecord>(`${ASSET_API_PREFIX}/changes/${id}/confirm`);

/** 忽略变更:仅关闭流水,不动台账。 */
export const ignoreChange = (id: number) =>
  HttpUtils.postData<boolean>(`${ASSET_API_PREFIX}/changes/${id}/ignore`);

// ---------- 目录维护 / 标签字典 / 编目规则 ----------

export const createDirectory = (params: DirectoryUpsertParams) =>
  HttpUtils.postData<DirNode>(`${ASSET_API_PREFIX}/directories`, params);

export const updateDirectory = (id: number, params: DirectoryUpsertParams) =>
  HttpUtils.putData<DirNode>(`${ASSET_API_PREFIX}/directories/${id}`, params);

export const moveDirectory = (id: number, targetParentId: number) =>
  HttpUtils.postData<boolean>(`${ASSET_API_PREFIX}/directories/${id}/move`, { targetParentId });

export const deleteDirectory = (id: number) =>
  HttpUtils.deleteData<boolean>(`${ASSET_API_PREFIX}/directories/${id}`);

/** 按分层+业务域一键建目录;已初始化 48008。 */
export const initDirectoryTemplate = () =>
  HttpUtils.postData<number>(`${ASSET_API_PREFIX}/directories/init-template`, {});

export const createAssetTag = (params: TagUpsertParams) =>
  HttpUtils.postData<AssetTagRecord>(`${ASSET_API_PREFIX}/tags`, params);

export const updateAssetTag = (id: number, params: TagUpsertParams) =>
  HttpUtils.putData<AssetTagRecord>(`${ASSET_API_PREFIX}/tags/${id}`, params);

export const deleteAssetTag = (id: number) =>
  HttpUtils.deleteData<boolean>(`${ASSET_API_PREFIX}/tags/${id}`);

export const listAssetRules = () =>
  HttpUtils.getData<RuleRecord[]>(`${ASSET_API_PREFIX}/rules`);

export const createAssetRule = (params: RuleUpsertParams) =>
  HttpUtils.postData<RuleRecord>(`${ASSET_API_PREFIX}/rules`, params);

export const updateAssetRule = (id: number, params: RuleUpsertParams) =>
  HttpUtils.putData<RuleRecord>(`${ASSET_API_PREFIX}/rules/${id}`, params);

export const deleteAssetRule = (id: number) =>
  HttpUtils.deleteData<boolean>(`${ASSET_API_PREFIX}/rules/${id}`);

export const dryRunAssetRule = (id: number) =>
  HttpUtils.postData<RuleDryRunResult>(`${ASSET_API_PREFIX}/rules/${id}/dry-run`);

/** 启用前置:必须先试跑(48010)。 */
export const enableAssetRule = (id: number) =>
  HttpUtils.postData<RuleRecord>(`${ASSET_API_PREFIX}/rules/${id}/enable`);

export const disableAssetRule = (id: number) =>
  HttpUtils.postData<RuleRecord>(`${ASSET_API_PREFIX}/rules/${id}/disable`);

export const applyAssetRuleAgain = (id: number) =>
  HttpUtils.postData<number>(`${ASSET_API_PREFIX}/rules/${id}/apply-again`);
