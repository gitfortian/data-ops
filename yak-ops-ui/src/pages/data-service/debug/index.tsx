import { history, useLocation } from '@umijs/max';
import { Button, Empty, Input, Select, Spin, message } from 'antd';
import { Play } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  fetchDataServiceDocumentation,
  fetchDataServices,
  testDataService,
  type DataServiceApi,
  type DataServiceDocumentation,
  type DataServiceQueryResult,
} from '../service';

const typeLabel: Record<string, string> = {
  STRING: 'String',
  INTEGER: 'Integer',
  NUMBER: 'Number',
  BOOLEAN: 'Boolean',
  DATE: 'Date',
  DATETIME: 'DateTime',
};

const prettyJson = (value: unknown) => JSON.stringify(value, null, 2);

export default function DataServiceDebugPage() {
  const location = useLocation();
  const requestedApiId = useMemo(
    () => Number(new URLSearchParams(location.search).get('apiId') || 0),
    [location.search],
  );

  const [services, setServices] = useState<DataServiceApi[]>([]);
  const [selectedApiId, setSelectedApiId] = useState<number>();
  const [documentation, setDocumentation] = useState<DataServiceDocumentation>();
  const [debugValues, setDebugValues] = useState<Record<string, string>>({});
  const [testResult, setTestResult] = useState<DataServiceQueryResult>();
  const [loading, setLoading] = useState(true);
  const [docLoading, setDocLoading] = useState(false);
  const [testing, setTesting] = useState(false);
  const serviceLoadStartedRef = useRef(false);

  const selectedService = useMemo(
    () => services.find((item) => Number(item.id) === Number(selectedApiId)),
    [selectedApiId, services],
  );

  const apiOptions = useMemo(
    () => services.map((item) => ({
      value: item.id,
      label: item.name,
    })),
    [services],
  );

  const loadServices = useCallback(async () => {
    try {
      const response = await fetchDataServices();
      setServices(response.data || []);
    } catch (error: any) {
      message.error(error?.message || '加载 API 列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (serviceLoadStartedRef.current) return;
    serviceLoadStartedRef.current = true;
    void loadServices();
  }, [loadServices]);

  useEffect(() => {
    if (loading) return;
    if (!services.length) {
      setSelectedApiId(undefined);
      return;
    }

    const requested = services.find(
      (item) => Number(item.id) === requestedApiId,
    );
    const preferred = requested
      || services.find((item) => item.enabled)
      || services[0];

    if (!preferred) return;

    const preferredId = Number(preferred.id);
    setSelectedApiId((current) => (
      Number(current) === preferredId ? current : preferred.id
    ));

    if (preferredId !== requestedApiId) {
      history.replace(`/data-service/debug?apiId=${preferred.id}`);
    }
  }, [loading, requestedApiId, services]);

  useEffect(() => {
    if (!selectedApiId) {
      setDocumentation(undefined);
      setDebugValues({});
      setTestResult(undefined);
      return;
    }

    let cancelled = false;
    setDocLoading(true);
    setTestResult(undefined);

    void fetchDataServiceDocumentation(selectedApiId)
      .then((response) => {
        if (cancelled) return;
        const doc = response.data;
        setDocumentation(doc);
        const nextValues: Record<string, string> = {};
        for (const parameter of doc?.parameters || []) {
          nextValues[parameter.name] = parameter.example || '';
        }
        setDebugValues(nextValues);
      })
      .catch((error: any) => {
        if (!cancelled) {
          setDocumentation(undefined);
          setDebugValues({});
          message.error(error?.message || '加载 API 参数失败');
        }
      })
      .finally(() => {
        if (!cancelled) setDocLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedApiId]);

  const handleSelectApi = (value: number) => {
    setSelectedApiId(value);
    history.replace(`/data-service/debug?apiId=${value}`);
  };

  const runTest = async () => {
    if (!selectedService) return;

    const missing = (documentation?.parameters || []).find(
      (item) => item.required && !String(debugValues[item.name] || '').trim(),
    );
    if (missing) {
      message.warning(`请输入参数 ${missing.name}`);
      return;
    }

    setTesting(true);
    try {
      const response = await testDataService(selectedService.id, debugValues);
      if (!response.data) {
        throw new Error(response.message || response.msg || '调试失败');
      }
      setTestResult(response.data);
    } catch (error: any) {
      message.error(error?.message || '调试失败');
    } finally {
      setTesting(false);
    }
  };

  const requestDetail = useMemo(() => {
    if (!selectedService) return '';
    return prettyJson({
      method: 'GET',
      endpoint: selectedService.runtimePath,
      auth: selectedService.authMode === 'API_KEY' ? 'API Key' : 'Public',
      parameters: debugValues,
    });
  }, [debugValues, selectedService]);

  const responseContent = useMemo(() => {
    if (!testResult) return '';
    return prettyJson({
      columns: testResult.columns,
      rows: testResult.rows,
      rowCount: testResult.rowCount,
      truncated: testResult.truncated,
    });
  }, [testResult]);

  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-white">
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div className="h-[calc(100vh-64px)] overflow-hidden bg-white text-[#161823]">
      <div className="flex h-12 items-center px-5">
        <div className="text-[17px] font-semibold text-[#161823]">API 测试</div>
      </div>

      <div className="h-[calc(100%-48px)] px-5 pb-5">
        <div className="grid h-full min-h-0 overflow-hidden lg:grid-cols-[minmax(380px,0.86fr)_minmax(520px,1.14fr)]">
          <section className="flex min-h-0 flex-col pr-5">
            <div className="flex min-h-[44px] items-center border-b border-[#eef0f2] text-[14px] font-semibold">
              请求配置
            </div>

            <div className="flex min-h-0 flex-1 flex-col overflow-y-auto pt-5">
              <Select
                showSearch
                variant="filled"
                value={selectedApiId}
                options={apiOptions}
                optionFilterProp="label"
                placeholder="请选择或搜索 API"
                className="w-full"
                onChange={handleSelectApi}
                notFoundContent="暂无 API"
              />

              {selectedService ? (
                <div className="mt-3 flex items-center gap-2 text-[11px] text-[#8a8f98]">
                  <span
                    className={[
                      'inline-block h-2 w-2 rounded-full',
                      selectedService.enabled ? 'bg-[#20c77a]' : 'bg-[#b0b5bd]',
                    ].join(' ')}
                  />
                  <span>{selectedService.enabled ? '运行中' : '已停用'}</span>
                  <span className="text-[#d0d5dd]">·</span>
                  <span className="truncate font-mono">GET {selectedService.runtimePath}</span>
                </div>
              ) : null}

              <div className="mt-6 text-[13px] font-semibold text-[#344054]">请求参数</div>

              <div className="mt-3 flex-1">
                {docLoading ? (
                  <div className="flex min-h-[180px] items-center justify-center">
                    <Spin size="small" />
                  </div>
                ) : (documentation?.parameters || []).length ? (
                  <div className="space-y-4">
                    {(documentation?.parameters || []).map((parameter) => (
                      <div key={parameter.name}>
                        <div className="mb-1.5 flex items-center gap-2 text-[12px]">
                          <span className="font-medium text-[#344054]">{parameter.name}</span>
                          <span className="text-[#98a2b3]">{typeLabel[parameter.type] || parameter.type}</span>
                          {parameter.required ? (
                            <span className="text-[10px] text-[var(--yak-brand-color)]">必填</span>
                          ) : null}
                        </div>
                        <Input
                          value={debugValues[parameter.name] || ''}
                          placeholder={parameter.example || `请输入 ${parameter.name}`}
                          onChange={(event) => setDebugValues((current) => ({
                            ...current,
                            [parameter.name]: event.target.value,
                          }))}
                        />
                        {parameter.description ? (
                          <div className="mt-1 text-[11px] text-[#98a2b3]">
                            {parameter.description}
                          </div>
                        ) : null}
                      </div>
                    ))}
                  </div>
                ) : selectedService ? (
                  <div className="bg-[#f7f7f8] px-4 py-3 text-[12px] text-[#8a8f98]">
                    当前 API 无请求参数
                  </div>
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择 API" />
                )}
              </div>

              <div className="mt-5 border-t border-[#eef0f2] pt-4">
                <Button
                  type="primary"
                  icon={<Play size={14} />}
                  disabled={!selectedService}
                  loading={testing}
                  onClick={() => void runTest()}
                >
                  开始测试
                </Button>
              </div>
            </div>
          </section>

          <section className="grid min-h-0 border-l border-[#eef0f2] pl-5 lg:grid-rows-[minmax(220px,0.42fr)_minmax(280px,0.58fr)]">
            <div className="flex min-h-0 flex-col border-b border-[#eef0f2]">
              <div className="flex min-h-[44px] items-center border-b border-[#f2f3f5] text-[13px] font-semibold">
                请求详情
              </div>
              <div className="min-h-0 flex-1 overflow-auto bg-[#fbfbfc] p-4">
                {selectedService ? (
                  <pre className="m-0 whitespace-pre-wrap break-words font-mono text-[12px] leading-6 text-[#475467]">
                    {requestDetail}
                  </pre>
                ) : (
                  <div className="flex h-full items-center justify-center text-[12px] text-[#b0b5bd]">
                    请选择 API
                  </div>
                )}
              </div>
            </div>

            <div className="flex min-h-0 flex-col">
              <div className="flex min-h-[44px] items-center justify-between border-b border-[#f2f3f5]">
                <div className="text-[13px] font-semibold">返回内容</div>
                {testResult ? (
                  <div className="flex items-center gap-4 text-[11px] text-[#667085]">
                    <span className="font-medium text-[#20a66a]">200 OK</span>
                    <span>{testResult.durationMs} ms</span>
                    <span>{testResult.rowCount} 行</span>
                  </div>
                ) : null}
              </div>
              <div className="min-h-0 flex-1 overflow-auto bg-[#fafafa] p-4">
                {testResult ? (
                  <pre className="m-0 whitespace-pre-wrap break-words font-mono text-[12px] leading-6 text-[#30343b]">
                    {responseContent}
                  </pre>
                ) : (
                  <div className="flex h-full items-center justify-center text-[12px] text-[#b0b5bd]">
                    测试结果将在这里展示
                  </div>
                )}
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
