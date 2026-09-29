import { CheckCircle2, LoaderCircle, Variable } from 'lucide-react';
import type { NodeProps } from 'reactflow';
import WorkflowNodeHandle from '../node/WorkflowNodeHandle';
import type { WorkflowStartNodeData } from './types';

const TYPE_LABEL: Record<string, string> = {
  STRING: 'String',
  NUMBER: 'Number',
  BOOLEAN: 'Boolean',
  FILE: 'File',
  ARRAY_STRING: 'Array[String]',
};

const DifyStartIcon = () => (
  <svg
    viewBox="0 0 20 20"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    className="h-4 w-4"
  >
    <circle
      cx="7.1"
      cy="5.45"
      r="2.35"
      stroke="currentColor"
      strokeWidth="1.75"
    />
    <path
      d="M2.85 14.35c.45-2.65 2.05-4.05 4.35-4.05 1.26 0 2.3.32 3.08.95"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
    />
    <path
      d="m11.55 14.55 3.25-3.25a1.75 1.75 0 1 1 2.48 2.48l-3.25 3.25a1.75 1.75 0 0 1-2.48 0"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="m14.2 13.2-3.25 3.25a1.75 1.75 0 0 1-2.48-2.48l2.35-2.35"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

const WorkflowStartNode = ({ id, data, selected }: NodeProps<WorkflowStartNodeData>) => {
  const visibleInputs = data.inputs.slice(0, 3);
  const moreCount = Math.max(0, data.inputs.length - visibleInputs.length);
  const running = data.runtimeStatus === 'RUNNING';
  const succeeded = data.runtimeStatus === 'SUCCESS';

  return (
    <div className="group w-60 rounded-2xl bg-[#f2f4f7] px-0 pb-0 pt-0.5">
      <div className="mb-0.5 flex h-5 items-center px-2.5 pt-0.5">
        <span className="text-[10px] font-semibold uppercase leading-4 text-[#667085]">
          开始
        </span>
      </div>

      <div className="relative">
        <div
          className={[
            'relative overflow-hidden rounded-[15px] border bg-white pb-1',
            'shadow-[0_1px_2px_rgba(16,24,40,.06),0_1px_3px_rgba(16,24,40,.10)]',
            'transition-[border-color,box-shadow] duration-200',
            running
              ? 'border-[#155eef] shadow-[0_0_0_3px_rgba(21,94,239,.10)]'
              : succeeded
                ? 'border-[#12b76a] shadow-[0_0_0_2px_rgba(18,183,106,.07)]'
                : selected
                  ? 'border-[#155eef] shadow-[0_0_0_2px_rgba(21,94,239,.10)]'
                  : 'border-transparent group-hover:shadow-[0_8px_20px_rgba(16,24,40,.12)]',
          ].join(' ')}
        >
          <div className="flex items-center gap-2 px-3 pb-2 pt-3">
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border-[0.5px] border-white/20 bg-[#155eef] text-white shadow-[0_1px_2px_rgba(16,24,40,.08)]">
              <DifyStartIcon />
            </span>
            <div className="min-w-0 flex-1 truncate text-[13px] font-semibold leading-5 text-[#101828]">
              开始
            </div>
            {running ? (
              <span className="inline-flex h-5 items-center gap-1 rounded-md bg-[#eff4ff] px-1.5 text-[9px] font-medium text-[#175cd3]">
                <LoaderCircle size={10} className="animate-spin" />
                运行中
              </span>
            ) : succeeded ? (
              <span className="inline-flex h-5 items-center gap-1 rounded-md bg-[#ecfdf3] px-1.5 text-[9px] font-medium text-[#067647]">
                <CheckCircle2 size={10} />
                成功
              </span>
            ) : null}
          </div>

          {visibleInputs.length ? (
            <div className="mb-1 px-3 py-1">
              <div className="space-y-0.5">
                {visibleInputs.map((field) => (
                  <div
                    key={field.id}
                    className="flex h-6 items-center gap-1 rounded-md bg-[#f2f4f7] px-1 text-[10px]"
                  >
                    <Variable size={14} className="shrink-0 text-[#155eef]" />
                    <span className="min-w-0 flex-1 truncate text-[#475467]">
                      {field.name}
                    </span>
                    {field.required ? (
                      <span className="shrink-0 text-[9px] font-medium uppercase text-[#98a2b3]">
                        必填
                      </span>
                    ) : null}
                    <span className="shrink-0 text-[9px] text-[#98a2b3]">
                      {TYPE_LABEL[field.type] || field.type}
                    </span>
                  </div>
                ))}
                {moreCount ? (
                  <div className="px-1 text-[9px] text-[#98a2b3]">
                    还有 {moreCount} 个输入字段
                  </div>
                ) : null}
              </div>
            </div>
          ) : null}
        </div>

        <WorkflowNodeHandle
          nodeId={id}
          type="source"
          selected={selected}
          locked={data.locked}
          appendOptions={data.appendOptions}
          onAppend={data.onAppend}
        />
      </div>
    </div>
  );
};

export default WorkflowStartNode;
