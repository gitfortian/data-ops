import { SendOutlined } from '@ant-design/icons';
import { Select } from 'antd';

import DatabaseIcons from '@/components/data-source/icons/DatabaseIcons';
import { defaultOfflineSyncConnectorProfile } from './connectorProfiles';
import './index.less';

interface DataSourceType {
  value: string;
  label: React.ReactNode;
  icon?: React.ReactNode;
  connectorType?: string;
  pluginName?: string;
}

const executionMetadata = (dbType: string) => {
  const profile = defaultOfflineSyncConnectorProfile(dbType);
  return {
    connectorType: profile?.connectorType,
    pluginName: profile?.pluginName,
  };
};

const DATA_SOURCE_TYPES = [
  { value: 'MYSQL', displayName: 'MYSQL' },
  { value: 'TIDB', displayName: 'TiDB' },
  { value: 'GOLDENDB', displayName: 'GoldenDB' },
  { value: 'GBASE8C', displayName: 'GBase 8c' },
  { value: 'GBASE8A', displayName: 'GBase 8a' },
  { value: 'GBASE8S', displayName: 'GBase 8s' },
  { value: 'HANA', displayName: 'SAP HANA' },
  { value: 'ORACLE', displayName: 'ORACLE' },
  { value: 'POSTGRE_SQL', displayName: 'PostgreSQL' },
  { value: 'DB2', displayName: 'IBM Db2' },
  { value: 'OPEN_GAUSS', displayName: 'openGauss' },
  { value: 'SQL_SERVER', displayName: 'SQL Server' },
  { value: 'OCEANBASE', displayName: 'OceanBase' },
  { value: 'YASHAN_DB', displayName: 'YashanDB' },
  { value: 'HIGHGO', displayName: 'HighGo' },
  { value: 'IRIS', displayName: 'InterSystems IRIS' },
  { value: 'XUGU', displayName: 'XuguDB' },
  { value: 'DUCKDB', displayName: 'DuckDB' },
  { value: 'DORIS', displayName: 'Doris' },
  { value: 'STARROCKS', displayName: 'StarRocks' },
  { value: 'CLICKHOUSE', displayName: 'ClickHouse' },
  { value: 'ELASTICSEARCH7', displayName: 'Elasticsearch 7' },
  { value: 'ELASTICSEARCH8', displayName: 'Elasticsearch 8' },
  { value: 'MONGODB', displayName: 'MongoDB' },
  { value: 'KINGBASE', displayName: 'KINGBASE' },
  { value: 'DAMENG', displayName: 'DAMENG' },
] as const;

/** UI presentation stays here; execution identity comes exclusively from Connector Profiles. */
export const generateDataSourceOptions = (): DataSourceType[] =>
  DATA_SOURCE_TYPES.map(({ value, displayName }) => ({
    value,
    ...executionMetadata(value),
    label: (
      <div style={{ display: 'flex', alignItems: 'center' }}>
        <DatabaseIcons dbType={value} width="24px" height="24px" />
        <span style={{ marginLeft: 8 }}>{displayName}</span>
      </div>
    ),
  }));

interface DataSourceSelectProps {
  value: any;
  onChange: (value: string, option: any) => void;
  placeholder: string;
  prefix: string;
  dataSourceOptions: any[];
  width?: string;
}

export const DataSourceSelect: React.FC<DataSourceSelectProps> = ({
  value,
  onChange,
  placeholder,
  prefix,
  dataSourceOptions,
  width = '42%',
}) => (
  <Select
    showSearch
    className="custom-ant-select-selector"
    placeholder={placeholder}
    value={value?.dbType}
    optionFilterProp="value"
    onChange={onChange}
    suffixIcon={<SendOutlined />}
    style={{ width, borderRadius: 24 }}
    prefix={<span style={{ fontSize: 12, fontWeight: 500 }}>{prefix}</span>}
    filterOption={(input, option) =>
      String(option?.value || '').toLowerCase().includes(input.toLowerCase())
    }
    options={dataSourceOptions}
  />
);

export default DataSourceSelect;
