import { YakButton } from '@/components/ui';
import { useIntl } from '@umijs/max';
import { Plus } from 'lucide-react';

interface DataSourcePageHeaderProps {
  canCreate: boolean;
  onCreate: () => void;
}

const DataSourcePageHeader = ({ canCreate, onCreate }: DataSourcePageHeaderProps) => {
  const intl = useIntl();

  return (
    <header className="source-page-header">
      <div>
        <h1 className="m-0 text-xl font-semibold leading-7 tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.datasource.page.title' })}
        </h1>
        <p className="source-page-subtitle">管理数据连接，即时探查数据库和表结构。</p>
      </div>

      {canCreate ? (
        <YakButton
          type="primary"
          icon={<Plus size={16} strokeWidth={2.1} />}
          className="!h-10 !shrink-0 !rounded-[8px] !px-4 !text-[13px]"
          onClick={onCreate}
        >
          {intl.formatMessage({ id: 'pages.datasource.page.create' })}
        </YakButton>
      ) : null}
    </header>
  );
};

export default DataSourcePageHeader;
