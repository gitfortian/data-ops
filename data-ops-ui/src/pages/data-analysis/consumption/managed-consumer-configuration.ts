import type { ConsumerRef } from '@/services/consumption';
import type { DataServiceConsumer } from '@/services/data-service/consumer';
import { parseManagedConsumerSourceId } from '@/config/consumer-source-navigation';

/** Read-only source-management snapshot from the current Project and actor. */
export interface ManagedConsumerReadSnapshot {
  state: ManagedConsumerSourceState;
  consumers: readonly DataServiceConsumer[];
}

/** The existing source controller is Project-scoped and requires data-service:access. */
export type ManagedConsumerSourceState = 'LOADING' | 'READY' | 'FORBIDDEN' | 'UNAVAILABLE';

export type ManagedConsumerConfigurationState =
  | 'NOT_APPLICABLE'
  | 'NOT_READABLE'
  | 'UNSAFE_ID'
  | 'NOT_FOUND'
  | 'DISABLED'
  | 'NOT_GRANTED'
  | 'NO_ACTIVE_KEYS'
  | 'CONFIGURED';

export interface ManagedConsumerConfiguration {
  state: ManagedConsumerConfigurationState;
  label: string;
  detail: string;
}

/** Source configuration is NOT Usage/Subscription/Approval or proof of a reachable runtime. */
export const inspectManagedConsumerConfiguration = (
  ref: ConsumerRef,
  productType: 'DATASET' | 'DATA_SERVICE',
  productSourceIdentity: string,
  state: ManagedConsumerSourceState,
  sourceConsumers: readonly DataServiceConsumer[],
): ManagedConsumerConfiguration => {
  if (productType !== 'DATA_SERVICE'
      || ref.consumerType !== 'DATA_SERVICE'
      || ref.sourceDomain !== 'DATA_SERVICE_CONSUMER') {
    return {
      state: 'NOT_APPLICABLE',
      label: '该类型无统一来源配置核对',
      detail: '保留稳定 Consumer 身份；不推断使用权、负责人或回执。',
    };
  }
  if (state !== 'READY') {
    return {
      state: 'NOT_READABLE',
      label: state === 'LOADING' ? '正在读取来源配置' : '来源配置未核实',
      detail: state === 'FORBIDDEN'
        ? '当前身份无调用方管理读取权限，不能断言来源对象不存在。'
        : state === 'UNAVAILABLE'
        ? '调用方来源读取失败，不能把未知当作已删除或无授权。'
        : '等待当前 Project 的调用方来源读取完成。',
    };
  }
  const apiId = parseManagedConsumerSourceId(productSourceIdentity);
  const consumerId = parseManagedConsumerSourceId(ref.sourceIdentity);
  if (apiId === null || consumerId === null) {
    return {
      state: 'UNSAFE_ID',
      label: '来源 ID 无法精确核对',
      detail: '源 ID 超出当前数值型调用方接口的安全范围，或不是规范十进制正整数；不做近似匹配。',
    };
  }
  const found = sourceConsumers.find((consumer) =>
    Number.isSafeInteger(consumer.id) && consumer.id === consumerId);
  if (!found) {
    return {
      state: 'NOT_FOUND',
      label: '当前 Project 未找到该调用方',
      detail: '当前可读取列表未包含此稳定身份，可能已删除；历史 Usage 证据仍有效，不代表现在无人受影响。',
    };
  }
  if (!found.enabled) {
    return {
      state: 'DISABLED',
      label: '来源调用方已停用',
      detail: '来源配置显示已停用；不能由此删除历史成功消费或推断变更已获确认。',
    };
  }
  const granted = found.accessScope === 'ALL'
    || (found.accessScope === 'SELECTED'
      && (found.apiIds || []).some((id) => Number.isSafeInteger(id) && id === apiId));
  if (!granted) {
    return {
      state: 'NOT_GRANTED',
      label: '当前未配置此 API 授权',
      detail: '当前来源授权清单未覆盖该精确服务；这与已知 Subscription/历史 Usage 是不同事实。',
    };
  }
  if (found.activeKeyCount === 0) {
    return {
      state: 'NO_ACTIVE_KEYS',
      label: '当前没有有效 API Key',
      detail: '调用方允许访问此 API，但来源显示 0 个有效 Key；具体 AuthMode/IP/Runtime 仍需来源核对。',
    };
  }
  return {
    state: 'CONFIGURED',
    label: '调用方已启用并配置此 API',
    detail: '来源显示 API 范围匹配且存在有效 Key；不代表网络策略、运行健康、当前实际消费或人工确认。',
  };
};

/**
 * Subscription selector must never match a rounded API id. This is an
 * authorization-configuration candidate, not proof of a usable key or a call.
 */
export const eligibleConfiguredDataServiceConsumers = (
  productSourceIdentity: string,
  consumers: readonly DataServiceConsumer[],
): DataServiceConsumer[] => {
  const apiId = parseManagedConsumerSourceId(productSourceIdentity);
  if (apiId === null) return [];
  return consumers.filter((consumer) =>
    Number.isSafeInteger(consumer.id) && consumer.id > 0
    && consumer.enabled
    && (consumer.accessScope === 'ALL'
      || (consumer.accessScope === 'SELECTED'
        && (consumer.apiIds || []).some((id) => Number.isSafeInteger(id) && id === apiId))));
};

/** Keep decisions on whether the source is loaded separate from empty source results. */
export const sourceConsumerAccessState = (
  canRead: boolean,
  loading: boolean,
  failed: boolean,
): ManagedConsumerSourceState => {
  if (!canRead) return 'FORBIDDEN';
  if (failed) return 'UNAVAILABLE';
  return loading ? 'LOADING' : 'READY';
};
