import MysqlIcon from '@/components/data-source/icons/MysqlIcon';
import OracleIcon from '@/components/data-source/icons/OracleIcon';
import { SendOutlined } from '@ant-design/icons';
import { Select } from 'antd';
import CacheIcon from '@/components/data-source/icons/CacheIcon';
import ClickhouseIcon from '@/components/data-source/icons/ClickhouseIcon';
import DaMengIcon from '@/components/data-source/icons/DamengIcon';
import DB2Icon from '@/components/data-source/icons/DB2Icon';
import DorisIcon from '@/components/data-source/icons/DorisIcon';
import HiveIcon from '@/components/data-source/icons/HiveIcon';
import MongoDBIcon from '@/components/data-source/icons/MongoDBIcon';
import OpenGaussIcon from '@/components/data-source/icons/OpenGaussIcon';
import PostgreSQL from '@/components/data-source/icons/PsSqlIcon';
import SQLServer from '@/components/data-source/icons/SQLServer';
import StarRocksIcon from '@/components/data-source/icons/StarRocksIcon';
import KingBaseIcon from '@/components/data-source/icons/KingBaseIcon';
import TiDBIcon from '@/components/data-source/icons/TiDBIcon';

const { Option } = Select;

type DataSourceType =
  | 'MYSQL'
  | 'ORACLE'
  | 'POSTGRE_SQL'
  | 'DORIS'
  | 'KINGBASE'
  | 'DAMENG'

type DataSourceSelectorProps = {
  type: 'source' | 'target';
  value?: string;
  onChange: (value: string) => void;
  style?: React.CSSProperties;
  dataSources?: DataSourceType[]; // 可配置的数据源列表
};

// 数据源配置映射
const DATA_SOURCE_CONFIG: Record<
  DataSourceType,
  { icon: React.ComponentType<any>; displayName: string }
> = {
  MYSQL: { icon: MysqlIcon, displayName: 'MySQL' },
  ORACLE: { icon: OracleIcon, displayName: 'ORACLE' },
  POSTGRE_SQL: { icon: PostgreSQL, displayName: 'PostgreSQL' },
  DORIS: { icon: DorisIcon, displayName: 'Doris' },
  KINGBASE: { icon: KingBaseIcon, displayName: 'Kingbase' },
  DAMENG: { icon: DaMengIcon, displayName: 'Dameng' }
};

// 默认支持的数据源
const DEFAULT_DATA_SOURCES: DataSourceType[] = [
  'MYSQL',
  'ORACLE',
  'POSTGRE_SQL',
  'DORIS',
  'KINGBASE',
  'DAMENG'
];

const DataSourceSelector = ({
  type,
  value,
  onChange,
  style,
  dataSources = DEFAULT_DATA_SOURCES,
}: DataSourceSelectorProps) => {
  const renderDataSourceOption = (dataSourceType: DataSourceType) => {
    const config = DATA_SOURCE_CONFIG[dataSourceType];
    if (!config) {
      console.warn(`Unknown data source type: ${dataSourceType}`);
      return null;
    }

    const { icon: IconComponent, displayName } = config;

    return (
      <Option
        style={{ paddingLeft: 12 }}
        value={dataSourceType}
        key={dataSourceType}
        label={displayName}
      >
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <IconComponent />
          <span style={{ marginLeft: 8 }}>{displayName}</span>
        </div>
      </Option>
    );
  };

  return (
    <Select
      showSearch
      value={value}
      placeholder={`Select ${type} data source`}
      optionFilterProp="label"
      onChange={onChange}
      suffixIcon={<SendOutlined />}
      style={style}
      filterOption={(input, option) =>
        (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
      }
    >
      {dataSources.map(renderDataSourceOption)}
    </Select>
  );
};

export default DataSourceSelector;
