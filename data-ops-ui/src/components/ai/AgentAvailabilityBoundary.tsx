import { Button, Result, Spin } from 'antd';
import type { ReactNode } from 'react';
import { useAgentAvailability } from '@/contexts/AgentAvailabilityContext';

/** Keep the assistant subtree unmounted until the deployment explicitly enables it. */
export default function AgentAvailabilityBoundary({ children }: { children: ReactNode }) {
  const { agentEnabled, loading, failed, refresh } = useAgentAvailability();
  if (loading) return <div className="p-8" role="status"><Spin /> 正在确认智能助手是否可用…</div>;
  if (agentEnabled === true) return <>{children}</>;
  return <Result status={failed ? 'warning' : 'info'}
    title={failed ? '暂时无法确认智能助手是否可用' : '智能助手未开启'}
    subTitle={failed ? '请稍后重试，或联系管理员检查服务连接。' : '当前部署未开启智能助手，请联系管理员开启后重新检查。'}
    extra={<Button onClick={refresh}>{failed ? '重试' : '重新检查'}</Button>} />;
}
