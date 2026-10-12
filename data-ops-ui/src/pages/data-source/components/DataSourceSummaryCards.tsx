import type { DataSourceSummary } from '@/services/data-source';
import { useIntl } from '@umijs/max';

const SUMMARY_ITEMS = [
  { key: 'total', labelId: 'pages.datasource.summary.total', tone: '' },
  { key: 'connected', labelId: 'pages.datasource.summary.connected', tone: 'connected' },
  { key: 'disconnected', labelId: 'pages.datasource.summary.disconnected', tone: 'disconnected' },
  { key: 'unknown', labelId: 'pages.datasource.status.unknown', tone: 'unknown' },
] as const;

const DataSourceSummaryCards = ({
  summary,
  unavailable = false,
}: {
  summary: DataSourceSummary;
  unavailable?: boolean;
}) => {
  const intl = useIntl();
  return (
    <section className="source-summary" aria-label="当前项目连接概况">
      {SUMMARY_ITEMS.map((item) => (
        <div className={`source-summary-item ${item.tone}`} key={item.key}>
          <span className="source-summary-dot" aria-hidden="true" />
          <span>{intl.formatMessage({ id: item.labelId })}</span>
          <strong>{unavailable ? '—' : summary[item.key]}</strong>
        </div>
      ))}
      <span className="source-summary-note">{unavailable ? '连接概况暂未取得' : '连接检测与表结构采集分别管理'}</span>
    </section>
  );
};

export default DataSourceSummaryCards;
