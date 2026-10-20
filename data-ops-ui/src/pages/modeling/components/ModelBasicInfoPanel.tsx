import type { ReactNode } from 'react';
import { Box, Database, FolderTree, Table2 } from 'lucide-react';
import type { ModelingModelRecord } from '@/services/modeling/types';
import { MODELING_DIALECT_LABELS, MODELING_STATUS_LABELS } from '../constants';
import './ModelBasicInfoPanel.less';

interface Props {
  model: ModelingModelRecord;
  layerLabel: string;
  databaseName: string;
  datasourceName: string;
  sourceDatasourceName: string | null;
  sourceFullTable: string | null;
}

function InfoField({ label, value, code = false, emptyText = '—' }: {
  label: string; value?: string | null; code?: boolean; emptyText?: string;
}) {
  const present = Boolean(value?.trim());
  return (
    <div className="model-basic-info-field">
      <dt>{label}</dt>
      <dd className={present ? undefined : 'is-empty'}>
        {present ? (code ? <code>{value}</code> : value) : emptyText}
      </dd>
    </div>
  );
}

function InfoSection({ title, icon, className = '', children }: {
  title: string; icon: ReactNode; className?: string; children: ReactNode;
}) {
  return (
    <section className={`model-basic-info-card ${className}`} aria-label={title}>
      <header className="model-basic-info-card-header">{icon}<h3>{title}</h3></header>
      {children}
    </section>
  );
}

/** Read-only presentation of existing model, layer configuration and source labels. */
export default function ModelBasicInfoPanel({
  model, layerLabel, databaseName, datasourceName, sourceDatasourceName, sourceFullTable,
}: Props) {
  return (
    <div className="model-basic-info">
      <h2 className="modeling-studio-panel-title">基本信息</h2>
      <div className="model-basic-info-grid">
        <InfoSection title="模型信息" icon={<Box size={16} aria-hidden />} className="model-basic-info-identity">
          <dl className="model-basic-info-identity-fields">
            <InfoField label="模型名称" value={model.name} />
            <InfoField label="模型编码" value={model.code} code />
            <InfoField label="模型分层" value={layerLabel} />
            <div className="model-basic-info-field">
              <dt>状态</dt>
              <dd>
                <span className="model-basic-info-status" data-status={model.status}>
                  <i aria-hidden />
                  {MODELING_STATUS_LABELS[model.status || ''] || model.status || '—'}
                </span>
              </dd>
            </div>
          </dl>
          <dl className="model-basic-info-description">
            <InfoField label="描述" value={model.description} />
          </dl>
        </InfoSection>

        <InfoSection title="业务归属" icon={<FolderTree size={16} aria-hidden />}>
          <dl className="model-basic-info-business-fields">
            <InfoField label="业务域" value={model.domainName} emptyText="未关联" />
            <InfoField label="业务过程" value={model.processName} emptyText="未关联" />
          </dl>
        </InfoSection>

        <InfoSection title="物理目标" icon={<Database size={16} aria-hidden />}>
          <dl className="model-basic-info-target-fields">
            <InfoField label="目标数据源" value={datasourceName} />
            <InfoField label="目标库" value={databaseName} code />
            <InfoField label="方言" value={MODELING_DIALECT_LABELS[model.dialect || ''] || model.dialect} />
          </dl>
        </InfoSection>

        {(sourceDatasourceName || sourceFullTable) && (
          <InfoSection title="来源信息" icon={<Table2 size={16} aria-hidden />} className="model-basic-info-source">
            <dl className="model-basic-info-source-fields">
              <InfoField label="来源数据源" value={sourceDatasourceName} />
              <InfoField label="来源表" value={sourceFullTable} code />
            </dl>
          </InfoSection>
        )}
      </div>
    </div>
  );
}
