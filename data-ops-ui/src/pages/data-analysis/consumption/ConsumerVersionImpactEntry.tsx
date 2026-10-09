import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { readConsumerVersionImpactTarget } from '@/services/agent/consumerVersionImpact';
import { governanceEntryPath } from '@/services/agent/governance';
import type { DataProductView } from '@/services/consumption';
import { productKeyValue } from '@/services/consumption';
import { history, useParams } from '@umijs/max';
import { Button } from 'antd';

export default function ConsumerVersionImpactEntry({ product, version, blocked }: {
  product: DataProductView;
  version?: string;
  blocked: boolean;
}) {
  const { currentProject } = useSecurityProject();
  const { can } = usePermissionAccess();
  const { productKey } = useParams<{ productKey: string }>();
  let routeKey;
  try { routeKey = decodeURIComponent(productKey || ''); } catch { return null; }
  if (blocked || !currentProject || String(currentProject.id) !== String(product.projectId)
      || routeKey !== productKeyValue(product.productKey)
      || !can('data-asset:read') || !can('agent:chat:run')) return null;
  let target;
  try {
    target = readConsumerVersionImpactTarget({
      productType: product.productKey.productType,
      productIdentity: product.productKey.sourceIdentity,
      sourceVersionIdentity: version,
    });
  } catch { return null; }
  if (!target) return null;
  return <Button size="small" onClick={() => history.push(governanceEntryPath({
    purpose: 'CONSUMER_VERSION_IMPACT', consumerVersionImpact: target,
  }))}>AI 消费影响说明</Button>;
}
