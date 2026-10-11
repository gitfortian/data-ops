import { history } from '@umijs/max';
import { Alert, Button, Card, Select, Space, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import {
  getDevelopmentRelease, listDevelopmentReleases,
} from '@/services/data-development';
import { listDatasetsForManagement, type DatasetManagementItem } from '@/services/dataset';
import {
  consumptionProductPath, getConsumerImpact, getProduct, productKeyValue,
} from '@/services/consumption';
import {
  findExactSqlRelease, inspectExactDatasetConsumption, linkCurrentDatasetsToRelease,
  type DatasetConsumptionProof, type DatasetSourceProof, type ReleaseLookup,
} from '../datasetConsumptionProof';

interface Props {
  nodeId: string;
  projectId: string | number | undefined;
}

/** Read-only, explicit lookup. Never generates SQL, ModelVersionRef, or consumption. */
export default function DatasetConsumptionProofPanel({ nodeId, projectId }: Props) {
  const [loadKey, setLoadKey] = useState(0);
  const [requested, setRequested] = useState(false);
  const [loading, setLoading] = useState(false);
  const [releaseLookup, setReleaseLookup] = useState<ReleaseLookup>();
  const [sourceProof, setSourceProof] = useState<DatasetSourceProof>();
  const [problem, setProblem] = useState<string>();
  const [datasetId, setDatasetId] = useState<string>();
  const [usage, setUsage] = useState<DatasetConsumptionProof>();
  const [usageLoading, setUsageLoading] = useState(false);
  const [usageProblem, setUsageProblem] = useState<string>();

  useEffect(() => {
    let active = true;
    setReleaseLookup(undefined);
    setSourceProof(undefined);
    setProblem(undefined);
    setDatasetId(undefined);
    setUsage(undefined);
    setUsageProblem(undefined);
    if (!requested) return () => { active = false; };
    if (projectId == null || !Number.isSafeInteger(Number(projectId)) || Number(projectId) <= 0) {
      setProblem('当前项目无法核实，拒绝跨域读取 Dataset');
      return () => { active = false; };
    }
    setLoading(true);
    void listDevelopmentReleases({ pageNo: 1, pageSize: 100, taskType: 'SQL' })
      .then(async page => {
        if (!active) return;
        const found = findExactSqlRelease(nodeId, page);
        setReleaseLookup(found);
        if (found.state !== 'FOUND') return;
        const [detail, datasets] = await Promise.all([
          getDevelopmentRelease(found.release.assetId),
          listDatasetsForManagement(),
        ]);
        if (!active) return;
        const proof = linkCurrentDatasetsToRelease(found.release, detail, datasets);
        if (proof.state === 'INVALID_RELEASE') setProblem(proof.reason);
        else setSourceProof(proof);
      }).catch((error: unknown) => {
        if (active) setProblem(error instanceof Error ? error.message
          : 'DataDev 发布或 Dataset 目录证据不可访问，不能推断关联为空');
      }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [requested, loadKey, nodeId, projectId]);

  const datasets = sourceProof?.state === 'MATCHED' ? sourceProof.datasets : [];
  const selected = datasets.find(item => item.id === datasetId);
  useEffect(() => {
    let active = true;
    setUsage(undefined);
    setUsageProblem(undefined);
    if (!selected) return () => { active = false; };
    if (!selected.currentVersion?.id) return () => { active = false; };
    setUsageLoading(true);
    const productKey = productKeyValue({ productType: 'DATASET', sourceIdentity: selected.id });
    void Promise.all([
      getProduct(productKey),
      getConsumerImpact(productKey, selected.currentVersion.id),
    ]).then(([lookup, impact]) => {
      if (!active) return;
      const observed = inspectExactDatasetConsumption(selected, Number(projectId), lookup, impact);
      if (observed.state === 'INVALID_OWNER') setUsageProblem(observed.reason);
      else setUsage(observed);
    }).catch((error: unknown) => {
      if (active) setUsageProblem(error instanceof Error ? error.message
        : 'Consumption 产品或使用数据不可访问，不能认为消费为零');
    }).finally(() => { if (active) setUsageLoading(false); });
    return () => { active = false; };
  }, [selected?.id, selected?.currentVersion?.id, projectId, loadKey]);

  return (
    <Card size="small" className="!mt-3"
      title="G2 · SQL 修订 → Dataset 来源版本 → 受治理消费证据（只读）"
      extra={<Button size="small" loading={loading}
        onClick={() => { setRequested(true); setLoadKey(x => x + 1); }}>
        {requested ? '重新核验跨域证据' : '人工核验下游 Dataset'}
      </Button>}>
      <Alert showIcon type="info" className="!mb-3"
        message="仅识别真实 TaskAsset + TaskRevisionId 引用，不按 SQL 名称猜测业务关联"
        description="DataDev 发布版本、Dataset 当前来源版本、受治理消费是三种独立事实。该路径未建立 Modeling 物理版本到 SQL 的正式映射；质量验收与运行血缘仍需各自的精确原域证据。" />
      {loading && <Typography.Paragraph type="secondary">
        正在读取当前项目 DataDev 发布中心与 Dataset 目录…
      </Typography.Paragraph>}
      {problem && <Alert showIcon type="error" className="!mb-3"
        message="下游来源证据无法核实" description={problem} />}
      {releaseLookup?.state === 'INVALID' && <Alert showIcon type="error"
        message="TaskAsset 发布身份不可信" description={releaseLookup.reason} />}
      {releaseLookup?.state === 'NOT_FOUND_IN_WINDOW' && <Alert showIcon type="warning"
        message="此查询范围没有观察到该 SQL 节点的发布 TaskAsset"
        description={`已检查本项目当前分页 ${releaseLookup.examined} / ${releaseLookup.total} 个发布对象，${releaseLookup.complete
          ? '当前 SQL 发布列表已覆盖' : '只查询前 100 项，不能断言未发布'}；不代表节点不存在或没有历史版本。`} />}
      {releaseLookup?.state === 'FOUND' && <Space wrap className="!mb-3">
        <Tag>DataDev TaskAsset #{releaseLookup.release.assetId}</Tag>
        <Tag color={releaseLookup.release.status === 'ONLINE' ? 'green' : 'orange'}>
          {releaseLookup.release.status}
        </Tag>
        <Tag>当前任务修订 V{releaseLookup.release.currentRevisionNo}</Tag>
        <Typography.Text type="secondary">
          当前项目 SQL 发布列表检查 {releaseLookup.examined} / {releaseLookup.total} 条
          {releaseLookup.complete ? '' : '（仅第一页）'}
        </Typography.Text>
      </Space>}
      {sourceProof?.state === 'MATCHED' && (
        <>
          <Alert showIcon type={datasets.length ? 'info' : 'warning'} className="!mb-3"
            message={datasets.length
              ? `找到 ${datasets.length} 个当前版本精确引用该 TaskAsset 修订的 Dataset`
              : '当前 Dataset 目录没有发现精确匹配的来源修订'}
            description={`核验了当前项目 Dataset 目录 ${sourceProof.totalDatasets} 项的当前版本。
这里不包含历史 Dataset 版本，也不是未经执行验证的业务交付证明。`} />
          {datasets.length > 0 && <Space wrap className="!mb-3">
            <Select<string> showSearch optionFilterProp="label" allowClear
              style={{ width: 350 }} placeholder="选择精确来源匹配的 Dataset"
              value={datasetId} onChange={setDatasetId}
              options={datasets.map(d => ({
                value: d.id, label: `${d.name} · Dataset #${d.id} · V${d.currentVersion?.versionNo}`,
              }))} />
            {selected && <Button onClick={() => history.push(
              consumptionProductPath('DATASET', selected.id))}>
              进入 Consumption 原域核对
            </Button>}
          </Space>}
          {selected && <Typography.Paragraph type="secondary">
            Dataset 当前版本 #{selected.currentVersion?.id} ·
            来源 TaskAsset #{selected.currentVersion?.sourceTaskAssetId} ·
            DataDev 修订 #{selected.currentVersion?.sourceTaskRevisionId}。
            Dataset 状态：{selected.status}。
          </Typography.Paragraph>}
        </>
      )}
      {usageLoading && <Typography.Paragraph type="secondary">
        正在从 Consumption 读取当前 Dataset 精确版本的使用证据…
      </Typography.Paragraph>}
      {usageProblem && <Alert showIcon type="error" className="!mb-3"
        message="消费证据身份或权限不可核实" description={usageProblem} />}
      {usage && usage.state !== 'INVALID_OWNER' && <Alert showIcon className="!mb-3"
        type={usage.state === 'OBSERVED' ? 'info' : 'warning'}
        message={usage.state === 'OBSERVED'
          ? 'Consumption 观察到当前 Dataset 版本的成功消费回执'
          : usage.state === 'UNAVAILABLE'
            ? '消费来源不可用或尚未达到受治理可访问状态'
            : '本次 Consumption 证据窗口未观察到精确版本的成功消费'}
        description={`Dataset 版本 ID ${usage.activeVersionId} · Usage State ${usage.usageState}
· 匹配消费者版本的成功消费观察计数 ${usage.successfulUsage}
（不保证消费者间去重，也不代表全历史）。${usage.coverageNote}
这是受治理使用回执，不证明对应 Modeling 版本、数据质量规则或业务验收已通过。`} />}
    </Card>
  );
}
