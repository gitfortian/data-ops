import { CheckOutlined, CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { Input, message, Popconfirm, Spin } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';

import {
  deleteSystemEnvVar,
  listSystemEnvVars,
  saveSystemEnvVars,
  type EnvVarEntry,
} from '@/services/system/envVars';

const KV_ROW = 'flex items-center gap-2 rounded-md border border-[#eaecf0] px-3 py-2 transition-colors hover:border-[#d1d5db]';
const KEY_CLS = 'shrink-0 font-mono text-[13px] font-semibold text-[#161823]';
const EQ_CLS = 'text-[#d1d5db] select-none';
const VAL_CLS = 'min-w-0 flex-1 truncate font-mono text-[13px] text-[#475569]';
const ICON_BTN = 'shrink-0 flex items-center justify-center w-6 h-6 rounded hover:bg-[#f5f5f5] cursor-pointer text-[#98a2b3] hover:text-[#475569] transition-colors';

const EnvironmentSettingsPanel = () => {
  const [entries, setEntries] = useState<EnvVarEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // Inline-add state
  const [adding, setAdding] = useState(false);
  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');
  const newKeyRef = useRef<Input>(null);

  // Edit state
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editingValue, setEditingValue] = useState('');

  const fetchEntries = useCallback(() => {
    setLoading(true);
    listSystemEnvVars()
      .then((response) => {
        setEntries(response.data || []);
      })
      .catch(() => message.warning('环境变量读取失败'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    fetchEntries();
  }, [fetchEntries]);

  const handleAdd = async () => {
    const trimmedKey = newKey.trim();
    if (!trimmedKey) {
      message.warning('变量名不能为空');
      return;
    }
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(trimmedKey)) {
      message.warning('变量名只能包含字母、数字和下划线，且不能以数字开头');
      return;
    }
    const existing = entries.find((e) => e.key === trimmedKey);
    if (existing && existing.source === 'app') {
      message.warning(`变量 ${trimmedKey} 已存在，请直接编辑`);
      return;
    }

    setSaving(true);
    try {
      await saveSystemEnvVars({ [trimmedKey]: newValue });
      message.success('环境变量已添加');
      setAdding(false);
      setNewKey('');
      setNewValue('');
      fetchEntries();
    } catch {
      message.error('添加失败');
    } finally {
      setSaving(false);
    }
  };

  const startAdding = () => {
    setAdding(true);
    setNewKey('');
    setNewValue('');
    setTimeout(() => newKeyRef.current?.focus(), 0);
  };

  const cancelAdding = () => {
    setAdding(false);
    setNewKey('');
    setNewValue('');
  };

  const handleSaveEdit = async (key: string, value: string) => {
    setSaving(true);
    try {
      await saveSystemEnvVars({ [key]: value });
      setEditingKey(null);
      setEditingValue('');
      fetchEntries();
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (key: string) => {
    setSaving(true);
    try {
      await deleteSystemEnvVar(key);
      message.success('环境变量已删除');
      fetchEntries();
    } catch {
      message.error('删除失败');
    } finally {
      setSaving(false);
    }
  };

  const startEditing = (entry: EnvVarEntry) => {
    setEditingKey(entry.key);
    setEditingValue(entry.value);
  };

  const cancelEditing = () => {
    setEditingKey(null);
    setEditingValue('');
  };

  const appEntries = entries.filter((e) => e.source === 'app');

  const systemEntries = entries.filter((e) => e.source === 'system');

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spin size="small" />
      </div>
    );
  }

  return (
    <div className="text-[13px] text-[#344054]">
      <div className="mb-4 flex items-center justify-between">
        <div className="text-[17px] font-semibold text-[#161823]">环境变量</div>
        <span className="text-[11px] text-[#98a2b3]">
          {saving ? '保存中…' : `${appEntries.length} 项`}
        </span>
      </div>

      {/* App-configured env vars */}
      <div className="space-y-1.5">
        {appEntries.map((entry) => (
          <div key={entry.key} className={`${KV_ROW} group`}>
            {editingKey === entry.key ? (
              <>
                <span className={KEY_CLS}>{entry.key}</span>
                <span className={EQ_CLS}>=</span>
                <Input
                  className="flex-1 font-mono"
                  value={editingValue}
                  onChange={(e) => setEditingValue(e.target.value)}
                  onPressEnter={() => handleSaveEdit(entry.key, editingValue)}
                  size="small"
                  autoFocus
                />
                <span
                  className={`${ICON_BTN} text-[#12b76a]`}
                  onClick={() => handleSaveEdit(entry.key, editingValue)}
                >
                  <CheckOutlined />
                </span>
                <span className={ICON_BTN} onClick={cancelEditing}>
                  <CloseOutlined />
                </span>
              </>
            ) : (
              <>
                <span
                  className={KEY_CLS}
                  onClick={() => startEditing(entry)}
                  style={{ cursor: 'pointer' }}
                >
                  {entry.key}
                </span>
                <span className={EQ_CLS}>=</span>
                <span
                  className={VAL_CLS}
                  onClick={() => startEditing(entry)}
                  style={{ cursor: 'pointer' }}
                >
                  {entry.value || <span className="text-[#d1d5db]">空</span>}
                </span>
                <span
                  className={`${ICON_BTN} opacity-0 group-hover:opacity-100`}
                  onClick={() => startEditing(entry)}
                >
                  <EditOutlined />
                </span>
                <Popconfirm
                  title={`删除 ${entry.key}？`}
                  onConfirm={() => handleDelete(entry.key)}
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                >
                  <span
                    className={`${ICON_BTN} text-[#f04438] opacity-0 group-hover:opacity-100 hover:!text-[#f04438]`}
                  >
                    <DeleteOutlined />
                  </span>
                </Popconfirm>
              </>
            )}
          </div>
        ))}

        {/* Inline add row */}
        {adding && (
          <div className={KV_ROW}>
            <Input
              ref={newKeyRef}
              className="w-[180px] font-mono"
              placeholder="变量名"
              value={newKey}
              onChange={(e) => setNewKey(e.target.value)}
              size="small"
              autoFocus
            />
            <span className={EQ_CLS}>=</span>
            <Input
              className="flex-1 font-mono"
              placeholder="变量值"
              value={newValue}
              onChange={(e) => setNewValue(e.target.value)}
              onPressEnter={handleAdd}
              size="small"
            />
            <span className={`${ICON_BTN} text-[#12b76a]`} onClick={handleAdd}>
              <CheckOutlined />
            </span>
            <span className={ICON_BTN} onClick={cancelAdding}>
              <CloseOutlined />
            </span>
          </div>
        )}

        {/* Empty state */}
        {appEntries.length === 0 && !adding && (
          <div className="rounded-md border border-dashed border-[#d1d5db] px-4 py-6 text-center text-[12px] text-[#98a2b3]">
            暂无环境变量
          </div>
        )}
      </div>

      {/* Add button */}
      {!adding && (
        <div className="mt-3">
          <span
            className="inline-flex cursor-pointer items-center gap-1 text-[13px] text-[#344054] hover:text-[#1d2939] transition-colors"
            onClick={startAdding}
          >
            <PlusOutlined />
            添加
          </span>
        </div>
      )}

      {/* System defaults (read-only) */}
      {systemEntries.length > 0 && (
        <div className="mt-8 border-t border-[#eaecf0] pt-6">
          <div className="mb-4 text-[14px] font-semibold text-[#1d2939]">系统环境变量</div>
          <div className="max-h-[300px] overflow-auto rounded-md border border-[#f2f4f7]">
            {systemEntries.map((entry) => (
              <div
                key={entry.key}
                className="flex items-center gap-2 border-b border-[#f5f5f5] px-3 py-2 last:border-b-0"
              >
                <span className="shrink-0 font-mono text-[12px] font-semibold text-[#344054]">
                  {entry.key}
                </span>
                <span className={EQ_CLS}>=</span>
                <span className="min-w-0 flex-1 truncate font-mono text-[12px] text-[#98a2b3]">
                  {entry.value}
                </span>
              </div>
            ))}
          </div>

        </div>
      )}
    </div>
  );
};

export default EnvironmentSettingsPanel;
