import { InputNumber, Select } from 'antd';
import type { ReactNode } from 'react';

import {
  allowsCapability,
  CONNECTOR_CAPABILITY,
  type EndpointCapabilityState,
} from '../capabilities';
import type { SyncEditorState } from '../model';
import EditorSection from './EditorSection';

interface ChannelConfigSectionProps {
  editor: SyncEditorState;
  sinkConfig: Record<string, any>;
  sinkCapability: EndpointCapabilityState;
  onChange: (value: SyncEditorState) => void;
  onSinkChange: (patch: Record<string, any>) => void;
}

function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div>
      <div className="mb-2 text-[12px] font-medium text-[#475467]">
        {label}
      </div>
      {children}
    </div>
  );
}

export default function ChannelConfigSection({
  editor,
  sinkConfig,
  sinkCapability,
  onChange,
  onSinkChange,
}: ChannelConfigSectionProps) {
  const supportsDirtyDataHandling = allowsCapability(
    sinkCapability,
    CONNECTOR_CAPABILITY.DIRTY_DATA_HANDLING,
  );
  const currentDirtyPolicy = editor.channel.dirtyDataPolicy;
  const dirtyPolicyOptions = [
    { label: '遇错停止', value: 'stop' },
    ...(supportsDirtyDataHandling
      ? [{ label: '跳过并继续', value: 'skip' }]
      : currentDirtyPolicy === 'skip'
        ? [{ label: '跳过并继续（当前不支持）', value: 'skip', disabled: true }]
        : []),
  ];

  const updateChannel = (patch: Partial<SyncEditorState['channel']>) => {
    onChange({
      ...editor,
      channel: {
        ...editor.channel,
        ...patch,
      },
    });
  };

  return (
    <EditorSection title="通道控制">
      <div className="grid grid-cols-3 gap-4 max-lg:grid-cols-2 max-sm:grid-cols-1">
        <Field label="Channel 并发数">
          <InputNumber
            min={1}
            max={128}
            variant="filled"
            className="!w-full"
            value={editor.channel.parallelism}
            onChange={(parallelism) =>
              updateChannel({ parallelism: Number(parallelism || 1) })
            }
          />
        </Field>

        <Field label="写入批次">
          <InputNumber
            min={1}
            max={100000}
            variant="filled"
            className="!w-full"
            value={Number(sinkConfig.batchSize || 1000)}
            onChange={(batchSize) =>
              onSinkChange({
                batchSize: Number(batchSize || 1000),
              })
            }
          />
        </Field>

        <Field label="传输限速">
          <Select
            variant="filled"
            value={
              editor.channel.speedLimitEnabled
                ? 'limited'
                : 'unlimited'
            }
            options={[
              { label: '不限速', value: 'unlimited' },
              { label: '按记录数限速', value: 'limited' },
            ]}
            className="w-full"
            onChange={(value) =>
              updateChannel({
                speedLimitEnabled: value === 'limited',
              })
            }
          />
        </Field>

        <Field label="每秒记录数">
          <InputNumber
            min={1}
            max={10000000}
            variant="filled"
            disabled={!editor.channel.speedLimitEnabled}
            className="!w-full"
            value={Number(editor.channel.recordsPerSecond || 10000)}
            onChange={(recordsPerSecond) =>
              updateChannel({
                recordsPerSecond: Number(recordsPerSecond || 10000),
              })
            }
          />
        </Field>

        <Field label="脏数据策略">
          <Select
            variant="filled"
            value={currentDirtyPolicy}
            options={dirtyPolicyOptions}
            className="w-full"
            onChange={(dirtyDataPolicy) =>
              updateChannel({
                dirtyDataPolicy,
                ...(dirtyDataPolicy === 'skip'
                  ? {}
                  : { dirtyDataLimit: 0 }),
              })
            }
          />
          {!supportsDirtyDataHandling && currentDirtyPolicy === 'skip' ? (
            <div className="mt-1.5 text-[11px] leading-5 text-[#b54708]">
              当前 Sink Connector 未声明 DIRTY_DATA_HANDLING，请改为遇错停止。
            </div>
          ) : null}
        </Field>

        <Field label="脏数据上限">
          <InputNumber
            min={0}
            max={10000000}
            variant="filled"
            disabled={currentDirtyPolicy !== 'skip'}
            className="!w-full"
            value={Number(editor.channel.dirtyDataLimit || 0)}
            onChange={(dirtyDataLimit) =>
              updateChannel({
                dirtyDataLimit: Math.max(0, Number(dirtyDataLimit || 0)),
              })
            }
          />
        </Field>
      </div>
    </EditorSection>
  );
}
