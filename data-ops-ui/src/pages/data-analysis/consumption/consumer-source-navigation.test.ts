import type { ConsumerRef } from '@/services/consumption';
import { consumerSourceTarget, parseManagedConsumerSourceId } from './consumer-source-navigation';

const consumer = (
  consumerType: ConsumerRef['consumerType'],
  sourceDomain: string,
  sourceIdentity: string,
): ConsumerRef => ({
  consumerType,
  sourceDomain,
  sourceIdentity,
  displayHint: 'This is a display label, not a key',
});

describe('known Consumer source-object navigation', () => {
  it('opens only exact Data Service managed consumer identity in existing access surface', () => {
    expect(consumerSourceTarget(consumer('DATA_SERVICE', 'DATA_SERVICE_CONSUMER', '900123'))).toEqual({
      href: '/data-service/access?consumerId=900123',
      label: '核对调用方配置',
      description: expect.stringContaining('不代表已找到授权签字人'),
      requiredPermission: 'data-service:access',
    });
  });

  it('uses Dashboard viewer route rather than displayHint or its mutable name', () => {
    expect(consumerSourceTarget(consumer('DASHBOARD', 'DASHBOARD', '76'))).toMatchObject({
      href: '/dashboard/76',
      label: '核对仪表盘',
    });
  });

  it('does not guess owner lookup for JOB, USER, TEAM or similarly named source domains', () => {
    expect(consumerSourceTarget(consumer('JOB', 'BATCH_JOB', '50'))).toBeNull();
    expect(consumerSourceTarget(consumer('USER', 'SECURITY_PRINCIPAL', 'bob'))).toBeNull();
    expect(consumerSourceTarget(consumer('TEAM', 'TEAM', '22'))).toBeNull();
    expect(consumerSourceTarget(consumer('DATA_SERVICE', 'DATA_SERVICE', '22'))).toBeNull();
    expect(consumerSourceTarget(consumer('DASHBOARD', 'WORKFLOW', '22'))).toBeNull();
    expect(consumerSourceTarget(consumer('DASHBOARD', 'DASHBOARD', '../admin'))).toBeNull();
  });

  it('never rounds an unsafe BIGINT source identity to a different consumer', () => {
    for (const id of [
      '9007199254740993', '0009', '0', '-1', ' 12', '12 ', '5.0',
      '1e3', '+1', '12/keys', '123?other=1', '',
    ]) {
      expect(consumerSourceTarget(consumer('DATA_SERVICE', 'DATA_SERVICE_CONSUMER', id))).toBeNull();
      expect(parseManagedConsumerSourceId(id)).toBeNull();
    }
    expect(consumerSourceTarget(consumer('DASHBOARD', 'DASHBOARD', '9007199254740993'))).toBeNull();
    expect(parseManagedConsumerSourceId('9007199254740991')).toBe(9007199254740991);
  });

  it('rejects malformed cross-project query references before selecting a real entity', () => {
    expect(parseManagedConsumerSourceId(null)).toBeNull();
    expect(parseManagedConsumerSourceId('42')).toBe(42);
    expect(parseManagedConsumerSourceId('42&projectId=9')).toBeNull();
    expect(parseManagedConsumerSourceId('999999999999999999999999999')).toBeNull();
  });
});
