import type { ConsumerRef, ProductType } from '@/services/consumption';
import type { DataServiceConsumer } from '@/services/data-service/consumer';
import { Tooltip, Typography } from 'antd';
import {
  inspectManagedConsumerConfiguration,
  type ManagedConsumerSourceState,
} from './managed-consumer-configuration';

const { Text } = Typography;

/** Read-side source config check only; never an owner, contact, delivery or approval assertion. */
export default function ManagedConsumerConfigurationHint({
  consumerRef, productType, productSourceIdentity, sourceState, consumers,
}: {
  consumerRef: ConsumerRef;
  productType: ProductType;
  productSourceIdentity: string;
  sourceState: ManagedConsumerSourceState;
  consumers: readonly DataServiceConsumer[];
}) {
  const result = inspectManagedConsumerConfiguration(
    consumerRef, productType, productSourceIdentity, sourceState, consumers,
  );
  if (result.state === 'NOT_APPLICABLE') return null;
  const isWarning = ['NOT_FOUND', 'DISABLED', 'NOT_GRANTED', 'NO_ACTIVE_KEYS', 'UNSAFE_ID'].includes(result.state);
  return (
    <Tooltip title={result.detail}>
      <Text type={isWarning ? 'warning' : 'secondary'}>
        来源配置：{result.label}
      </Text>
    </Tooltip>
  );
}
