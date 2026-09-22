import type { DevelopmentSqlDialect } from '@/services/data-development';
import { useIntl } from '@umijs/max';
import { Input, Modal, Select, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { getDevelopmentSqlDialectLabel } from '../sqlDatabaseProfiles';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNodeType,
} from '../types';

interface CreateDevelopmentNodeModalProps {
  open: boolean;
  type: DevelopmentNodeType;
  sqlDialect?: DevelopmentSqlDialect;
  directories: DevelopmentDirectory[];
  defaultDirectoryId?: DevelopmentId;
  loading?: boolean;
  onCancel: () => void;
  onNext: (
    type: DevelopmentNodeType,
    directoryId: DevelopmentId | undefined,
    name: string,
  ) => void;
}

const ROOT_VALUE = '__root__';

const CreateDevelopmentNodeModal = ({
  open,
  type,
  sqlDialect,
  directories,
  defaultDirectoryId,
  loading = false,
  onCancel,
  onNext,
}: CreateDevelopmentNodeModalProps) => {
  const intl = useIntl();
  const [directoryId, setDirectoryId] = useState<DevelopmentId>();
  const [name, setName] = useState('');

  const typeLabel = useMemo(() => {
    if (type === 'SQL') return getDevelopmentSqlDialectLabel(sqlDialect);
    if (type === 'SHELL') return 'Shell';
    if (type === 'PYTHON') return 'Python';
    if (type === 'JAVA') return 'Java';
    if (type === 'DATASET') {
      return intl
        .formatMessage({ id: 'pages.dataDevelopment.workspace.datasetNode' })
        .replace(/ Node$| 节点$/, '');
    }
    if (type === 'DATA_SERVICE') {
      return intl
        .formatMessage({ id: 'pages.dataDevelopment.workspace.dataServiceNode' })
        .replace(/ Node$| 节点$/, '');
    }
    return type;
  }, [intl, sqlDialect, type]);

  const pathOptions = useMemo(
    () => [
      { label: '/', value: ROOT_VALUE },
      ...directories.map((directory) => ({
        label: directory.path,
        value: directory.id,
      })),
    ],
    [directories],
  );

  useEffect(() => {
    if (!open) return;
    setDirectoryId(defaultDirectoryId);
    setName('');
  }, [defaultDirectoryId, open, sqlDialect, type]);

  const normalizedName = name.trim();

  const submit = () => {
    if (!normalizedName || loading) return;
    onNext(type, directoryId, normalizedName);
  };

  return (
    <Modal
      open={open}
      title={intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.title' })}
      width={600}
      okText={intl.formatMessage({ id: 'pages.dataDevelopment.common.confirm' })}
      cancelText={intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' })}
      confirmLoading={loading}
      okButtonProps={{ disabled: !normalizedName }}
      destroyOnClose
      maskClosable={!loading}
      closable={!loading}
      onCancel={onCancel}
      onOk={submit}
    >
      <div className="grid grid-cols-[88px_minmax(0,1fr)] items-center gap-y-3 pt-2">
        <Typography.Text className="text-[13px] text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          {intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.type' })}
        </Typography.Text>
        <div className="flex h-8 items-center rounded-md border border-[#d9d9d9] bg-[#fafafa] px-3 text-[13px] text-[#475467]">
          {typeLabel}
        </div>

        <Typography.Text className="text-[13px] text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          {intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.path' })}
        </Typography.Text>
        <Select
          value={directoryId ?? ROOT_VALUE}
          options={pathOptions}
          showSearch
          optionFilterProp="label"
          className="w-full"
          disabled={loading}
          onChange={(value) => {
            const selected = String(value);
            setDirectoryId(selected === ROOT_VALUE ? undefined : selected);
          }}
        />

        <Typography.Text className="text-[13px] text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          {intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.name' })}
        </Typography.Text>
        <Input
          autoFocus
          value={name}
          maxLength={128}
          disabled={loading}
          placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.namePlaceholder' })}
          onChange={(event) => setName(event.target.value)}
          onPressEnter={submit}
        />
      </div>
    </Modal>
  );
};

export default CreateDevelopmentNodeModal;
