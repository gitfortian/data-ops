import { YakButton } from '@/components/ui';
import {
  updateDataServiceConsumerAccess,
  type DataServiceAccessOverviewItem,
  type DataServiceConsumer,
  type DataServiceConsumerAccessScope,
} from '@/services/data-service';
import { Select, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';

interface ConsumerApiAccessPanelProps {
  consumer: DataServiceConsumer;
  apis: DataServiceAccessOverviewItem[];
  onChanged: (next: DataServiceConsumer) => void;
}

export default function ConsumerApiAccessPanel({
  consumer,
  apis,
  onChanged,
}: ConsumerApiAccessPanelProps) {
  const [scope, setScope] = useState<DataServiceConsumerAccessScope>(consumer.accessScope);
  const [apiIds, setApiIds] = useState<number[]>(consumer.apiIds || []);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setScope(consumer.accessScope);
    setApiIds(consumer.apiIds || []);
  }, [consumer]);

  const options = useMemo(
    () => apis.map((api) => ({
      value: api.apiId,
      label: `${api.name}  ·  ${api.path}`,
    })),
    [apis],
  );

  const save = async () => {
    setSaving(true);
    try {
      const next = await updateDataServiceConsumerAccess(consumer.id, {
        accessScope: scope,
        apiIds: scope === 'SELECTED' ? apiIds : [],
      });
      onChanged(next);
      message.success('API 权限已更新');
    } catch (error: any) {
      message.error(error?.message || '更新 API 权限失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="rounded-xl bg-white">
      <div className="px-7 pt-5">
        <h2 className="m-0 text-[17px] font-semibold leading-6 text-[#161823]">
          API 权限
        </h2>
      </div>

      <div className="px-7 py-6">
        <div className="grid gap-2 md:grid-cols-2">
          {([
            { key: 'ALL' as const, title: '所有数据服务', meta: `${apis.length} 个 API` },
            { key: 'SELECTED' as const, title: '指定 API', meta: `${apiIds.length} 个已选` },
          ]).map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => setScope(item.key)}
              className={[
                'rounded-lg border border-solid px-4 py-3 text-left transition-colors',
                scope === item.key
                  ? 'border-[#161823] bg-[#f7f7f8]'
                  : 'border-[#eceef1] bg-white hover:bg-[#fafafa]',
              ].join(' ')}
            >
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-2 text-[13px] font-medium text-[#161823]">
                  <span
                    className={[
                      'h-2 w-2 rounded-full',
                      scope === item.key ? 'bg-[#161823]' : 'bg-[#d0d5dd]',
                    ].join(' ')}
                  />
                  {item.title}
                </div>
                <span className="text-[10px] text-[#98a2b3]">{item.meta}</span>
              </div>
            </button>
          ))}
        </div>

        {scope === 'SELECTED' ? (
          <div className="mt-5 grid grid-cols-[116px_minmax(0,1fr)] items-start gap-5 max-md:grid-cols-1 max-md:gap-2">
            <div className="pt-2.5 text-[13px] font-medium text-[#344054]">
              可访问 API
            </div>
            <Select<number[]>
              mode="multiple"
              value={apiIds}
              onChange={setApiIds}
              variant="filled"
              options={options}
              optionFilterProp="label"
              placeholder="选择允许调用的数据服务"
              className="w-full"
              maxTagCount="responsive"
            />
          </div>
        ) : null}

        <div className="mt-6 flex justify-end border-t border-solid border-[#f0f0f0] pt-4">
          <YakButton loading={saving} onClick={() => void save()}>
            保存权限
          </YakButton>
        </div>
      </div>
    </section>
  );
}
