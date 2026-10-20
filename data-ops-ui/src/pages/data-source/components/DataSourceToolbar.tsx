import { YakButton, YakTab } from '@/components/ui';
import { useIntl } from '@umijs/max';
import { Input, Select } from 'antd';
import { Search } from 'lucide-react';
import { COMMON_DB_OPTIONS, getDataSourceEnvironmentTabs } from '../constants';

interface DataSourceToolbarProps {
  environment?: string;
  dbType?: string;
  keyword: string;
  hasActiveFilters: boolean;
  onEnvironmentChange: (value?: string) => void;
  onDbTypeChange: (value?: string) => void;
  onKeywordChange: (value: string) => void;
  onReset: () => void;
}
export default function DataSourceToolbar({
  environment, dbType, keyword, hasActiveFilters, onEnvironmentChange, onDbTypeChange,
  onKeywordChange, onReset,
}: DataSourceToolbarProps) {
  const intl = useIntl();
  const environments = getDataSourceEnvironmentTabs(intl);
  return <section className="source-management-toolbar" aria-label="数据源筛选">
    <YakTab size="small" activeKey={environment || 'all'} className="source-management-tabs"
      items={environments.map(item => ({ key: item.key, label: item.label }))}
      onChange={key => onEnvironmentChange(environments.find(item => item.key === key)?.value)} />
    <div className="source-management-filters">
      <Select className="!w-[158px]" allowClear showSearch value={dbType}
        placeholder="数据库类型" options={COMMON_DB_OPTIONS} onChange={onDbTypeChange}
        optionFilterProp="label" />
      <Input className="!w-[275px]" allowClear value={keyword} prefix={<Search size={15} />}
        placeholder="搜索数据源名称或连接地址" onChange={e => onKeywordChange(e.target.value)} />
      {hasActiveFilters ? <YakButton size="small" type="text" onClick={onReset}>重置筛选</YakButton> : null}
    </div>
  </section>;
}
