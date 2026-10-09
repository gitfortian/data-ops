/**
 * P0 PR 3/4: strict reconciliation of source-owned successful audits and normalized Usage.
 * Pure inspection only: does not authenticate, invoke, normalize or synthesize a success.
 */
function demand(condition, message) {
  if (!condition) throw new Error('Source/Usage evidence mismatch: ' + message);
}

function same(a, b) {
  return a != null && b != null && String(a) === String(b);
}

function impactHas(impact, ref) {
  return impact?.usageState === 'READY'
    && Array.isArray(impact.consumers)
    && impact.consumers.some(c => Array.isArray(c.providerEvidenceRefs)
      && c.providerEvidenceRefs.includes(ref));
}

export function verifySourceUsagePair(datasetGolden, dataServiceGolden,
    datasetConsumption, dataServiceConsumption) {
  const dataset = datasetGolden?.dataset;
  const query = datasetGolden?.query?.result;
  const sourceAudit = datasetGolden?.queryPerformance;
  const canonicalDataset = datasetConsumption?.canonical;
  const datasetImpact = datasetConsumption?.impact;
  const invocation = dataServiceGolden?.invocationRecord;
  const definition = dataServiceGolden?.dataService;
  const serviceUsage = dataServiceGolden?.usageNormalization;
  const usage = serviceUsage?.evidence;
  const canonicalService = dataServiceConsumption?.canonical;
  const serviceImpact = dataServiceConsumption?.impact;
  const project = datasetGolden?.projectId;

  demand(project != null && same(project, dataServiceGolden?.projectId),
    'Dataset and Data Service must have the same authenticated Project');
  demand(query?.queryId && same(query.queryId, sourceAudit?.queryId),
    'Dataset response must match its persisted source audit queryId');
  demand(sourceAudit?.status === 'SUCCESS', 'Dataset source audit must record real SUCCESS');
  demand(same(dataset?.id, query?.datasetId)
    && same(dataset?.id, sourceAudit?.datasetId),
    'Dataset source object identity mismatch');
  demand(same(dataset?.currentVersionId, query?.datasetVersionId)
    && same(query?.datasetVersionId, sourceAudit?.datasetVersionId)
    && same(query?.datasetVersionId, canonicalDataset?.activeVersion?.identity),
    'Dataset exact source-version identity mismatch');
  demand(same(query?.datasetVersionNo, sourceAudit?.datasetVersionNo)
    && same(query?.datasetVersionNo, dataset?.currentVersionNo),
    'Dataset source-version number mismatch');
  const queryRef = 'DATASET_QUERY_PERFORMANCE:query:' + query.queryId;
  demand(impactHas(datasetImpact, queryRef),
    'Dataset impact lacks exact persisted successful query evidence reference');

  demand(invocation?.success === true && invocation?.id != null,
    'Data Service invocation must have durable successful audit id');
  demand(same(invocation?.projectId, project)
    && same(invocation?.apiId, definition?.id),
    'Data Service invocation source Project/product mismatch');
  demand(same(invocation?.sourceRevisionId, definition?.sourceRevisionId)
    && same(invocation?.sourceRevisionId, canonicalService?.activeVersion?.identity),
    'Data Service exact source revision mismatch');
  demand(same(invocation?.sourceRevisionNo, definition?.sourceRevisionNo),
    'Data Service exact revision number mismatch');
  demand(invocation?.consumerId != null && invocation?.apiKeyId != null,
    'Data Service must retain managed consumer and API key identities in source audit');
  demand(serviceUsage?.state === 'NORMALIZED' && usage?.outcome === 'SUCCESS',
    'Data Service Usage must be normalized from source success, not a simulated row');
  demand(same(usage?.projectId, project) && same(usage?.consumerRef?.sourceIdentity, invocation?.consumerId),
    'Data Service normalized Usage must preserve source Project and managed Consumer identity');
  demand(same(usage?.sourceVersion?.identity, invocation?.sourceRevisionId),
    'Data Service normalized Usage must preserve exact source revision');
  const invocationRef = 'invocation:' + invocation.id;
  demand(serviceUsage.providerEvidenceRef === invocationRef
    && usage?.providerEvidenceRef === invocationRef,
    'Data Service normalized Usage must point back to the exact invocation audit');
  demand(impactHas(serviceImpact, 'DATA_SERVICE_INVOCATION:' + invocationRef),
    'Data Service impact lacks exact persisted successful invocation reference');

  return {
    datasetSourceSuccess: true,
    datasetExactAuditAndVersionLinked: true,
    datasetImpactSourceAuditLinked: true,
    dataServiceSourceSuccess: true,
    dataServiceExactAuditRevisionConsumerLinked: true,
    dataServiceNormalizedUsageAndImpactLinked: true,
  };
}
