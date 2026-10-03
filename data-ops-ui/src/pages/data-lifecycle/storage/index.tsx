import { Alert, Card, InputNumber, Modal, Radio, Statistic, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import ReactECharts from 'echarts-for-react';
import { YakButton } from '@/components/ui';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { getStoragePrice, getStorageStats, getStorageTrend, updateStoragePrice } from '@/services/data-lifecycle/api';
import type { StorageStats, TrendPoint } from '@/services/data-lifecycle/types';
import { formatBytes, LAYER_LABELS } from '../constants';

const GB = 1024 * 1024 * 1024;

const StoragePage = () => {
  const { can } = usePermissionAccess();
  const canUpdate = can('data-lifecycle:update');
  const [stats, setStats] = useState<StorageStats | null>(null);
  const [trend, setTrend] = useState<TrendPoint[]>([]);
  const [trendDays, setTrendDays] = useState(30);
  const [loading, setLoading] = useState(false);
  const [priceOpen, setPriceOpen] = useState(false);
  const [price, setPrice] = useState<number | null>(null);
  const [savingPrice, setSavingPrice] = useState(false);
  const [loadError, setLoadError] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const [statsResult, priceResult] = await Promise.all([getStorageStats(), getStoragePrice()]);
      setStats(statsResult);
      if (priceResult?.pricePerGbMonth != null) setPrice(Number(priceResult.pricePerGbMonth));
    } catch {
      setLoadError(true);
      message.error('加载存储统计失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    getStorageTrend(trendDays)
      .then((points) => setTrend(points ?? []))
      .catch(() => setTrend([]));
  }, [trendDays]);

  const layerOption = {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 60, right: 24, top: 32, bottom: 32 },
    xAxis: {
      type: 'category' as const,
      data: (stats?.byLayer ?? []).map((l) => LAYER_LABELS[l.layerCode.toUpperCase()] ?? l.layerCode),
    },
    yAxis: { type: 'value' as const, name: 'GB' },
    series: [
      {
        type: 'bar' as const,
        barMaxWidth: 48,
        itemStyle: { color: '#1677ff', borderRadius: [4, 4, 0, 0] },
        data: (stats?.byLayer ?? []).map((l) => Number((l.sizeBytes / GB).toFixed(2))),
        label: {
          show: true,
          position: 'top' as const,
          formatter: (p: { dataIndex: number }) =>
            formatBytes(stats?.byLayer?.[p.dataIndex]?.sizeBytes ?? 0),
        },
      },
    ],
  };

  const trendOption = {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 60, right: 24, top: 32, bottom: 32 },
    xAxis: { type: 'category' as const, data: trend.map((p) => p.date) },
    yAxis: { type: 'value' as const, name: 'GB' },
    series: [
      {
        type: 'line' as const,
        smooth: true,
        areaStyle: { opacity: 0.12 },
        itemStyle: { color: '#7c3aed' },
        data: trend.map((p) => Number((p.sizeBytes / GB).toFixed(2))),
      },
    ],
  };

  const hot = stats?.hotCold?.hotBytes ?? 0;
  const cold = stats?.hotCold?.coldBytes ?? 0;
  const total = hot + cold > 0 ? hot + cold : (stats?.totalBytes ?? 0);
  const hasSnapshot = Boolean(stats?.snapshotDate);
  const unknownValue = loading ? '读取中' : '—';

  const savePrice = async () => {
    if (price == null || price <= 0) {
      message.warning('请输入有效的每 GB 每月单价');
      return;
    }
    setSavingPrice(true);
    try {
      await updateStoragePrice(String(price));
      message.success('单价已保存，成本将重新估算');
      setPriceOpen(false);
      await load();
    } catch {
      // 全局错误提示已展示原因
    } finally {
      setSavingPrice(false);
    }
  };

  return (
    <div className="flex min-h-[calc(100dvh-64px)] flex-col bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">存储统计</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            各层存储量、冷热分布与成本估算
            {stats?.snapshotDate ? ` · 快照日期：${stats.snapshotDate}` : ''}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {canUpdate && (
            <YakButton className="!h-9 !rounded-lg !px-4" onClick={() => setPriceOpen(true)}>
              设置存储单价
            </YakButton>
          )}
          <YakButton className="!h-9 !rounded-lg !px-4" onClick={() => void load()}>
            刷新
          </YakButton>
        </div>
      </div>

      {loadError && <Alert className="mt-4" type="error" showIcon message="存储统计读取失败" description="请刷新重试；已读取的数据仍保留在下方。" />}
      {!hasSnapshot && !loading && !loadError && (
        <Alert
          className="mt-4"
          type="info"
          showIcon
          message="尚无存储快照"
          description="采集完成后将展示存储量、冷热分布与成本。尚无快照时，这些指标未计算。"
        />
      )}

      <div className="mt-4 grid grid-cols-4 gap-4 max-md:grid-cols-2 max-sm:grid-cols-1">
        <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
          <Statistic title="总存储量" value={hasSnapshot ? formatBytes(stats?.totalBytes ?? 0) : unknownValue} valueStyle={{ fontSize: 22 }} />
        </div>
        <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
          <Statistic title="热存储" value={hasSnapshot ? formatBytes(hot) : unknownValue} valueStyle={{ fontSize: 22, color: hasSnapshot ? '#b54708' : '#667085' }} />
          {stats?.hotCold.estimated && <div className="text-[12px] text-[#667085]">按保留期估算</div>}
        </div>
        <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
          <Statistic title="冷存储" value={hasSnapshot ? formatBytes(cold) : unknownValue} valueStyle={{ fontSize: 22, color: hasSnapshot ? '#175cd3' : '#667085' }} />
        </div>
        <div className="rounded-lg border border-[#e5e7eb] px-4 py-3">
          <Statistic
            title="本月估算成本"
            value={hasSnapshot && stats?.monthlyCost != null ? `¥ ${Number(stats.monthlyCost).toFixed(2)}` : unknownValue}
            valueStyle={{ fontSize: 22, color: '#344054' }}
          />
          <div className="text-[12px] text-[#667085]">
            {stats?.pricePerGbMonth ? `单价 ¥${stats.pricePerGbMonth} / GB / 月` : '未设置单价'}
          </div>
        </div>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-4 max-lg:grid-cols-1">
        <Card title="各层存储量" size="small" loading={loading}>
          {total > 0 ? (
            <ReactECharts option={layerOption} style={{ height: 280 }} notMerge />
          ) : (
            <div className="flex h-[280px] items-center justify-center text-[13px] text-[#667085]">暂无数据</div>
          )}
        </Card>
        <Card
          title="存储量趋势"
          size="small"
          extra={
            <Radio.Group
              size="small"
              value={trendDays}
              onChange={(e) => setTrendDays(e.target.value as number)}
              options={[
                { value: 7, label: '近 7 天' },
                { value: 30, label: '近 30 天' },
                { value: 90, label: '近 90 天' },
              ]}
              optionType="button"
            />
          }
        >
          {trend.length > 0 ? (
            <ReactECharts option={trendOption} style={{ height: 280 }} notMerge />
          ) : (
            <div className="flex h-[280px] items-center justify-center text-[13px] text-[#667085]">
              趋势需要连续多日快照
            </div>
          )}
        </Card>
      </div>

      <Modal
        open={priceOpen}
        title="设置存储单价"
        okText="保存"
        cancelText="取消"
        confirmLoading={savingPrice}
        onOk={() => void savePrice()}
        onCancel={() => setPriceOpen(false)}
      >
        <div className="py-2">
          <div className="mb-2 text-[13px] text-[#667085]">用于估算每月存储成本（元 / GB / 月）</div>
          <InputNumber
            min={0.01}
            precision={4}
            addonAfter="元 / GB / 月"
            className="!w-full"
            value={price}
            onChange={(value) => setPrice(value)}
            placeholder="如 0.15"
          />
        </div>
      </Modal>
    </div>
  );
};

export default StoragePage;
