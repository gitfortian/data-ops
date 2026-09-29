import { useMemo } from 'react';
import { YakEmpty } from '@/components/ui';

type DiffStatus = 'added' | 'removed' | 'changed';

interface DiffRow {
  path: string;
  before: string;
  after: string;
  status: DiffStatus;
}

const stringify = (value: unknown): string => {
  if (value === undefined) return '-';
  if (value === null) return 'null';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};

/** 载荷允许是 JSON 字符串或已解析对象。 */
const parsePayload = (input: unknown): unknown => {
  if (typeof input !== 'string') return input;
  try {
    return JSON.parse(input);
  } catch {
    return input;
  }
};

const flatten = (value: unknown, prefix: string, out: Map<string, string>) => {
  if (Array.isArray(value)) {
    value.forEach((item, index) => flatten(item, `${prefix}[${index}]`, out));
  } else if (value && typeof value === 'object') {
    Object.entries(value as Record<string, unknown>).forEach(([key, item]) =>
      flatten(item, prefix ? `${prefix}.${key}` : key, out),
    );
  } else {
    out.set(prefix || '/', stringify(value));
  }
};

const buildRows = (beforeRaw: unknown, afterRaw: unknown): DiffRow[] => {
  const before = new Map<string, string>();
  const after = new Map<string, string>();
  flatten(parsePayload(beforeRaw), '', before);
  flatten(parsePayload(afterRaw), '', after);
  const paths = Array.from(new Set([...before.keys(), ...after.keys()])).sort();
  const rows: DiffRow[] = [];
  paths.forEach((path) => {
    const b = before.get(path);
    const a = after.get(path);
    if (b === a) return;
    if (b === undefined) rows.push({ path, before: '-', after: a ?? '-', status: 'added' });
    else if (a === undefined) rows.push({ path, before: b, after: '-', status: 'removed' });
    else rows.push({ path, before: b, after: a, status: 'changed' });
  });
  return rows;
};

const STATUS_STYLE: Record<DiffStatus, string> = {
  added: 'bg-[#f6ffed] text-[#389e0d]',
  removed: 'bg-[#fff1f0] text-[#cf1322]',
  changed: 'bg-[#fffbe6] text-[#d48806]',
};

const STATUS_LABEL: Record<DiffStatus, string> = {
  added: '新增',
  removed: '删除',
  changed: '修改',
};

/** 两版 JSON 载荷的字段级差异表(多版本契约:版本查看/diff)。 */
const JsonDiffView: React.FC<{ before: unknown; after: unknown }> = ({ before, after }) => {
  const rows = useMemo(() => buildRows(before, after), [before, after]);

  if (rows.length === 0) {
    return <YakEmpty compact title="两版内容一致" description="所选版本之间没有字段差异" />;
  }

  return (
    <div className="overflow-auto rounded-[8px] border border-[#e7e9ec]">
      <table className="w-full border-collapse text-[12px]">
        <thead>
          <tr className="bg-[#f9fafb] text-left text-[#667085]">
            <th className="px-3 py-2 font-medium">差异</th>
            <th className="px-3 py-2 font-medium">字段</th>
            <th className="px-3 py-2 font-medium">旧值</th>
            <th className="px-3 py-2 font-medium">新值</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.path} className="border-t border-[#eaecf0]">
              <td className="px-3 py-1.5">
                <span className={`rounded-[4px] px-1.5 py-0.5 ${STATUS_STYLE[row.status]}`}>
                  {STATUS_LABEL[row.status]}
                </span>
              </td>
              <td className="px-3 py-1.5 font-mono text-[#344054]">{row.path}</td>
              <td className="max-w-[280px] break-all px-3 py-1.5 text-[#98a2b3]">{row.before}</td>
              <td className="max-w-[280px] break-all px-3 py-1.5 text-[#344054]">{row.after}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="border-t border-[#eaecf0] bg-[#f9fafb] px-3 py-1.5 text-[11px] text-[#98a2b3]">
        共 {rows.length} 处字段差异（相同字段不展示）
      </div>
    </div>
  );
};

export default JsonDiffView;
