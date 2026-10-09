import HttpUtils from '@/utils/HttpUtils';

export async function getApplicationFeatures(): Promise<{ agentEnabled: boolean }> {
  // Deployment metadata uses the normal same-origin API; Agent/SSE keep their direct transport.
  const value = await HttpUtils.getData<{ agentEnabled: boolean }>('/api/v1/system/features', {
    skipErrorHandler: true,
  });
  if (typeof value?.agentEnabled !== 'boolean') throw new Error('部署功能状态无法确认');
  return value;
}
