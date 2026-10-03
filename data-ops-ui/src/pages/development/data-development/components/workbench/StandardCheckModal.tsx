import { useIntl } from '@umijs/max';
import { Button, Modal, Table, Tag } from 'antd';

import type {
  DevelopmentStandardCheck,
  DevelopmentStandardFieldCheck,
} from '@/services/data-development/types';

interface StandardCheckModalProps {
  open: boolean;
  report?: DevelopmentStandardCheck;
  onClose: () => void;
}

const ResultTag = ({ item }: { item: DevelopmentStandardFieldCheck }) => {
  const intl = useIntl();
  if (!item.evaluated) {
    return <Tag>{intl.formatMessage({ id: 'pages.dataDevelopment.standardCheck.unevaluated' })}</Tag>;
  }
  return item.matched ? (
    <Tag color="success">{intl.formatMessage({ id: 'pages.dataDevelopment.standardCheck.matched' })}</Tag>
  ) : (
    <Tag color="warning">{intl.formatMessage({ id: 'pages.dataDevelopment.standardCheck.unmatched' })}</Tag>
  );
};

const StandardCheckModal = ({ open, report, onClose }: StandardCheckModalProps) => {
  const intl = useIntl();
  const text = (id: string) => intl.formatMessage({ id });
  const items = report?.items ?? [];
  return (
    <Modal
      open={open}
      title={text('pages.dataDevelopment.standardCheck.title')}
      width={760}
      centered
      footer={
        <Button type="primary" onClick={onClose}>
          {text('pages.dataDevelopment.standardCheck.close')}
        </Button>
      }
      onCancel={onClose}
    >
      <div className="flex flex-col gap-2 py-1 text-[13px] text-[#475467]">
        {report?.parseError ? (
          <div className="text-[#f5222d]">
            {intl.formatMessage(
              { id: 'pages.dataDevelopment.standardCheck.parseError' },
              { message: report.parseError },
            )}
          </div>
        ) : (
          <div>
            {intl.formatMessage(
              { id: 'pages.dataDevelopment.standardCheck.summary' },
              { count: report?.fieldCount ?? 0 },
            )}
            {report?.truncated
              ? ` · ${intl.formatMessage(
                  { id: 'pages.dataDevelopment.standardCheck.truncated' },
                  { limit: items.length },
                )}`
              : null}
          </div>
        )}
      </div>
      <Table<DevelopmentStandardFieldCheck>
        size="small"
        rowKey="field"
        columns={[
          { title: text('pages.dataDevelopment.standardCheck.field'), dataIndex: 'field', width: 220 },
          {
            title: text('pages.dataDevelopment.standardCheck.result'),
            width: 110,
            render: (_, record) => <ResultTag item={record} />,
          },
          {
            title: text('pages.dataDevelopment.standardCheck.standard'),
            width: 240,
            render: (_, record) =>
              record.standardName || record.standardCode
                ? `${record.standardName ?? ''}${
                    record.standardCode ? `（${record.standardCode}）` : ''
                  }`
                : '-',
          },
          {
            title: text('pages.dataDevelopment.standardCheck.rule'),
            dataIndex: 'ruleExpr',
            render: (value: string | null | undefined) => value || '-',
          },
        ]}
        dataSource={items}
        pagination={false}
        scroll={items.length > 12 ? { y: 360 } : undefined}
        locale={{ emptyText: text('pages.dataDevelopment.standardCheck.empty') }}
      />
      <div className="mt-3 text-[12px] text-[#667085]">
        {text('pages.dataDevelopment.standardCheck.hint')}
      </div>
    </Modal>
  );
};

export default StandardCheckModal;
