import HttpUtils from '@/utils/HttpUtils';
import type {
  CollectJobQueryParams,
  CollectJobRecord,
  CollectJobUpsertParams,
  CollectRunRecord,
  EffectivePresencePolicy,
  EntityTypeView,
  MetadataChangeRecord,
  MetadataEntityDetail,
  MetadataEntityDto,
  MetadataOverviewData,
  MetadataPageResult,
  MetadataSearchParams,
  MetadataSearchResult,
} from './types';

const METADATA_API_PREFIX = '/api/v1/metadata';

/** Metadata specialist workbench overview: catalog inventory, collection and open tasks. */
export const getMetadataOverview = () =>
  HttpUtils.getData<MetadataOverviewData>(`${METADATA_API_PREFIX}/overview`);

type RawPage<T> = {
  bizData?: T[];
  records?: T[];
  total?: number;
  pages?: number;
  pageNo?: number;
  pageSize?: number;
  pagination?: { pageNo?: number; pageSize?: number; total?: number; pages?: number };
};

const toPageResult = <T>(raw: RawPage<T>, pageSize: number): MetadataPageResult<T> => {
  const pagination = raw?.pagination ?? {};
  return {
    records: raw?.bizData ?? raw?.records ?? [],
    total: pagination.total ?? raw?.total ?? 0,
    pages: pagination.pages ?? raw?.pages ?? 0,
    pageNo: pagination.pageNo ?? raw?.pageNo ?? 1,
    pageSize: pagination.pageSize ?? raw?.pageSize ?? pageSize,
  };
};

// ---------- 采集/对账任务（ticket 116 端点，页面见 ticket 122） ----------

export const pageCollectJobs = async (
  params: CollectJobQueryParams,
): Promise<MetadataPageResult<CollectJobRecord>> =>
  toPageResult(
    await HttpUtils.postData<RawPage<CollectJobRecord>>(`${METADATA_API_PREFIX}/collect-jobs/page`, params),
    params.pageSize,
  );

/** Runtime-authoritative HARVESTED Presence policy; independent of legacy job columns. */
export const getEffectivePresencePolicy = () =>
  HttpUtils.getData<EffectivePresencePolicy>(`${METADATA_API_PREFIX}/collect-jobs/effective-presence-policy`);

export const getCollectJob = (id: number) =>
  HttpUtils.getData<CollectJobRecord>(`${METADATA_API_PREFIX}/collect-jobs/${id}`);

export const createCollectJob = (params: CollectJobUpsertParams) =>
  HttpUtils.postData<CollectJobRecord>(`${METADATA_API_PREFIX}/collect-jobs`, params);

export const updateCollectJob = (id: number, params: CollectJobUpsertParams) =>
  HttpUtils.putData<CollectJobRecord>(`${METADATA_API_PREFIX}/collect-jobs/${id}`, params);

export const changeCollectJobEnabled = (id: number, enabled: boolean) =>
  HttpUtils.postData<CollectJobRecord>(`${METADATA_API_PREFIX}/collect-jobs/${id}/enabled`, { enabled });

export const deleteCollectJob = (id: number) =>
  HttpUtils.deleteData<boolean>(`${METADATA_API_PREFIX}/collect-jobs/${id}`);

/** 只算不写；后端在预演通过的瞬间点亮启用闸门,失败原因落在 RunView。 */
export const dryRunCollectJob = (id: number) =>
  HttpUtils.postData<CollectRunRecord>(`${METADATA_API_PREFIX}/collect-jobs/${id}/dry-run`);

/** 一轮采集是同步的：调用方直接拿到这一轮的计数,按钮要显示"本轮在跑"。 */
export const runCollectJob = (id: number) =>
  HttpUtils.postData<CollectRunRecord>(`${METADATA_API_PREFIX}/collect-jobs/${id}/run`);

export const pageCollectRuns = async (params: {
  pageNo: number;
  pageSize: number;
  jobId?: number;
}): Promise<MetadataPageResult<CollectRunRecord>> =>
  toPageResult(
    await HttpUtils.postData<RawPage<CollectRunRecord>>(`${METADATA_API_PREFIX}/collect-runs/page`, params),
    params.pageSize,
  );

// ---------- 实体详情聚合（ticket 118 端点，抽屉见 ticket 123） ----------

/** 一次取回「目录事实 + 分区」：块读没读到在各自状态里，响应本身只在实体不存在时失败。 */
export const getEntityDetail = (id: number) =>
  HttpUtils.getData<MetadataEntityDetail>(`${METADATA_API_PREFIX}/entities/${id}`);

/** 后端批量入口的硬上限（{@code CatalogQueryService.MAX_BATCH}），超限它只取前 200 条。 */
export const ENTITY_BATCH_LIMIT = 200;

/** 批量取实体（选择器/列表内联）：一条 IN，已撤销的不返回。 */
export const listEntities = (ids: number[]) => {
  const capped = ids.slice(0, ENTITY_BATCH_LIMIT);
  const search = new URLSearchParams();
  capped.forEach((id) => search.append('ids', String(id)));
  return HttpUtils.getData<MetadataEntityDto[]>(
    `${METADATA_API_PREFIX}/entities${capped.length ? `?${search}` : ''}`,
  );
};

/** 详情时间线的「更多」：只在历史块上翻页，不重跑整份聚合。 */
export const pageEntityChanges = async (
  id: number,
  pageNo: number,
  pageSize: number,
): Promise<MetadataPageResult<MetadataChangeRecord>> =>
  toPageResult(
    await HttpUtils.getData<RawPage<MetadataChangeRecord>>(
      `${METADATA_API_PREFIX}/entities/${id}/changes?pageNo=${pageNo}&pageSize=${pageSize}`,
    ),
    pageSize,
  );

// ---------- 统一搜索（ticket 117 端点，页面见 ticket 123） ----------

/**
 * GET /metadata/search：q 空 = 浏览模式，目录浏览与统一搜索因此共用这一条接口。
 * `index` 走重复参数（服务端收 List<String>），两个 filter 各自 JSON 化。
 */
export const searchMetadata = (params: MetadataSearchParams) => {
  const search = toSearchQuery(params);
  return HttpUtils.getData<MetadataSearchResult>(
    `${METADATA_API_PREFIX}/search${search ? `?${search}` : ''}`,
  );
};

const toSearchQuery = (params: MetadataSearchParams): string => {
  const search = new URLSearchParams();
  const q = params.q?.trim();
  if (q) search.set('q', q);
  (params.index ?? []).forEach((type) => search.append('index', type));
  if (params.queryFilter && Object.keys(params.queryFilter).length) {
    search.set('queryFilter', JSON.stringify(params.queryFilter));
  }
  if (params.postFilter && Object.keys(params.postFilter).length) {
    search.set('postFilter', JSON.stringify(params.postFilter));
  }
  if (params.sortField) search.set('sortField', params.sortField);
  if (params.sortOrder) search.set('sortOrder', params.sortOrder);
  if (params.searchAfter) search.set('searchAfter', params.searchAfter);
  if (params.from) search.set('from', String(params.from));
  if (params.size) search.set('size', String(params.size));
  // 布尔位只在为真时上 URL：默认 false 的开关写进串里只会让缓存键变多，不改变语义。
  if (params.getHierarchy) search.set('getHierarchy', 'true');
  if (params.explain) search.set('explain', 'true');
  return search.toString();
};

// ---------- 元模型（平台全局，供对账任务的实体类型下拉） ----------

export const listEntityTypes = (category?: string) =>
  HttpUtils.getData<EntityTypeView[]>(
    `${METADATA_API_PREFIX}/types${category ? `?category=${encodeURIComponent(category)}` : ''}`,
    undefined,
  );

/** F-039 Metadata-owned, permission-checked schema-only preview. No task or AI calls. */
export interface SourceSemanticEvidence {
  projectId: number;
  dataSourceId: string;
  database: string;
  schema: string;
  collectJobId: string;
  lastCollectAt: string;
  fingerprint: string;
  tables: Array<{
    assetKey: string;
    name: string;
    contentHash: string;
    declaredColumnCount: number;
    columns: Array<{
      name: string;
      contentHash: string;
      dataType: string;
      primaryKey: boolean;
      comment: string;
    }>;
  }>;
}

export const previewSourceSemanticEvidence = (dataSourceId: number, tableAssetKeys: string[]) =>
  HttpUtils.postData<SourceSemanticEvidence>(
    `${METADATA_API_PREFIX}/source-semantic/preview`,
    { dataSourceId, tableAssetKeys },
  );
