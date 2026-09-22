import {
  FileTextOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Input,
  Modal,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import { useMemo, useState } from 'react';

import { YakButton } from '@/components/ui';
import { importDepartments } from '@/services/security/departments';

import { getSystemErrorMessage } from '../../utils';
import { DEPARTMENT_IMPORT_EXAMPLE } from '../constants';
import {
  countDepartmentImportNodes,
  parseDepartmentImportJson,
} from '../utils';

interface DepartmentImportModalProps {
  open: boolean;
  onClose: () => void;
  onImported: () => void;
}

export default function DepartmentImportModal({
  open,
  onClose,
  onImported,
}: DepartmentImportModalProps) {
  const [source, setSource] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const preview = useMemo(() => {
    if (!source.trim()) {
      return { data: undefined, count: 0, error: undefined };
    }

    try {
      const data = parseDepartmentImportJson(source);
      return {
        data,
        count: countDepartmentImportNodes(data),
        error: undefined,
      };
    } catch (error) {
      return {
        data: undefined,
        count: 0,
        error: getSystemErrorMessage(
          error,
          '部门 JSON 校验失败',
        ),
      };
    }
  }, [source]);

  const close = () => {
    if (!isSaving) onClose();
  };

  const submit = async () => {
    if (!preview.data || isSaving) return;

    setIsSaving(true);
    try {
      await importDepartments(preview.data);
      message.success(`部门导入成功，共 ${preview.count} 个节点`);
      onClose();
      onImported();
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '部门导入失败'),
      );
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title="导入部门树"
      width={760}
      maskClosable={false}
      keyboard={!isSaving}
      closable={!isSaving}
      onCancel={close}
      afterClose={() => setSource('')}
      footer={
        <div className="flex items-center justify-end gap-2">
          <YakButton disabled={isSaving} onClick={close}>
            取消
          </YakButton>
          <YakButton
            type="primary"
            loading={isSaving}
            disabled={!preview.data}
            onClick={() => void submit()}
          >
            确认导入
          </YakButton>
        </div>
      }
    >
      <Alert
        showIcon
        type="info"
        className="mb-4"
        message="后端接收 JSON 部门树"
        description="请选择 JSON 文件或直接粘贴内容。系统会按层级生成部门 ID 和父子关系；数据库约束冲突会由后端返回错误。"
      />

      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Upload
            accept=".json,application/json"
            maxCount={1}
            showUploadList={false}
            beforeUpload={(file) => {
              void file
                .text()
                .then(setSource)
                .catch(() => message.error('JSON 文件读取失败'));
              return false;
            }}
          >
            <YakButton icon={<UploadOutlined />}>
              选择 JSON 文件
            </YakButton>
          </Upload>

          <YakButton
            type="text"
            icon={<FileTextOutlined />}
            onClick={() => setSource(DEPARTMENT_IMPORT_EXAMPLE)}
          >
            填充示例
          </YakButton>
        </div>

        {preview.data && (
          <Tag color="success">
            已识别 {preview.count} 个部门节点
          </Tag>
        )}
      </div>

      <Input.TextArea
        value={source}
        placeholder={DEPARTMENT_IMPORT_EXAMPLE}
        autoSize={{ minRows: 14, maxRows: 22 }}
        className="font-mono text-xs"
        status={preview.error ? 'error' : undefined}
        disabled={isSaving}
        onChange={(event) => setSource(event.target.value)}
      />

      <div className="mt-2 min-h-6">
        {preview.error ? (
          <Typography.Text type="danger">
            {preview.error}
          </Typography.Text>
        ) : (
          <Typography.Text type="secondary" className="text-xs">
            字段：deptName、description、childDeptDTOList。
          </Typography.Text>
        )}
      </div>
    </Modal>
  );
}
