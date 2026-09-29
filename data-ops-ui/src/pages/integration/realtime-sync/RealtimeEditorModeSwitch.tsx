import { CodeOutlined, FormOutlined } from '@ant-design/icons';
import { Segmented } from 'antd';
import type { RealtimeEditorMode } from './realtimeEditorMode';

export default function RealtimeEditorModeSwitch({
  value,
  disabled,
  onChange,
}: {
  value: RealtimeEditorMode;
  disabled?: boolean;
  onChange: (mode: RealtimeEditorMode) => void;
}) {
  return (
    <Segmented
      size="small"
      value={value}
      disabled={disabled}
      options={[
        { value: 'wizard', label: '向导模式', icon: <FormOutlined /> },
        { value: 'yaml', label: 'YAML 模式', icon: <CodeOutlined /> },
      ]}
      onChange={(next) => onChange(next as RealtimeEditorMode)}
    />
  );
}
