export interface ConsumerVersionImpactTarget {
  productType: 'DATASET' | 'DATA_SERVICE';
  productIdentity: string;
  sourceVersionIdentity: string;
}

export function readConsumerVersionImpactTarget(value: unknown): ConsumerVersionImpactTarget {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('消费版本上下文无效');
  const target = value as Record<string, unknown>;
  const { productType, productIdentity, sourceVersionIdentity } = target;
  if ((productType !== 'DATASET' && productType !== 'DATA_SERVICE') || typeof productIdentity !== 'string'
    || !/^[1-9][0-9]{0,18}$/.test(productIdentity)
    || (productIdentity.length === 19 && productIdentity > '9223372036854775807')
    || typeof sourceVersionIdentity !== 'string' || !/^[1-9][0-9]{0,29}$/.test(sourceVersionIdentity)
    || Object.keys(target).some((key) => !['productType', 'productIdentity', 'sourceVersionIdentity'].includes(key))) {
    throw new Error('请选择规范产品身份与精确来源版本');
  }
  return { productType, productIdentity, sourceVersionIdentity };
}

export const consumerVersionSourcePath = (target: ConsumerVersionImpactTarget) =>
  `/data-analysis/consumption/${target.productType}%3A${target.productIdentity}?reviewVersion=${target.sourceVersionIdentity}`;
