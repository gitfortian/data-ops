import type { ConsumerRef } from '@/services/consumption';
import {
  consumerSourceTarget,
  consumptionReviewReturnPath,
  parseConsumptionReviewReturnPath,
  parseManagedConsumerSourceId,
} from '@/config/consumer-source-navigation';

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
    expect(consumerSourceTarget(consumer('DASHBOARD', 'DASHBOARD', '9007199254740993'))).toMatchObject({
      href: '/dashboard/9007199254740993',
    });
    expect(parseManagedConsumerSourceId('9007199254740991')).toBe(9007199254740991);
  });

  it('rejects malformed cross-project query references before selecting a real entity', () => {
    expect(parseManagedConsumerSourceId(null)).toBeNull();
    expect(parseManagedConsumerSourceId('42')).toBe(42);
    expect(parseManagedConsumerSourceId('42&projectId=9')).toBeNull();
    expect(parseManagedConsumerSourceId('999999999999999999999999999')).toBeNull();
  });

  it('preserves an exact source ProductKey and immutable BIGINT revision in review return context', () => {
    const path = consumptionReviewReturnPath(
      'DATA_SERVICE', '9007199254740995', '9007199254740993',
    );
    expect(path).toBe('/data-analysis/consumption/DATA_SERVICE%3A9007199254740995?reviewVersion=9007199254740993');
    expect(parseConsumptionReviewReturnPath(path)).toBe(path);
    expect(consumptionReviewReturnPath('DATASET', '42')).toBe(
      '/data-analysis/consumption/DATASET%3A42',
    );
    const dataService = consumerSourceTarget(
      consumer('DATA_SERVICE', 'DATA_SERVICE_CONSUMER', '73'), path,
    );
    expect(dataService?.href).toContain('consumerId=73');
    expect(new URLSearchParams(dataService!.href.split('?')[1]).get('returnTo')).toBe(path);
    const dashboard = consumerSourceTarget(consumer('DASHBOARD', 'DASHBOARD', '9007199254740999'), path);
    expect(dashboard?.href).toContain('/dashboard/9007199254740999?');
    expect(new URLSearchParams(dashboard!.href.split('?')[1]).get('returnTo')).toBe(path);
  });

  it('never produces a navigable untrusted return or an external redirect', () => {
    const invalid = [
      null, '', 'https://attacker.example/steal',
      '//attacker.example/data-analysis/consumption/DATASET%3A1',
      'javascript:alert(1)', '/data-service/access?returnTo=/admin',
      '/data-analysis/consumption/DATASET%3A1/../../admin',
      '/data-analysis/consumption/DATASET%3A1#fragment',
      '/data-analysis/consumption/DATASET%3A1?reviewVersion=2&reviewVersion=3',
      '/data-analysis/consumption/DATASET%3A1?projectId=4',
      '/data-analysis/consumption/DATASET%3A1?reviewVersion=0',
      '/data-analysis/consumption/DATASET%3A1?reviewVersion=3%2fadmin',
      '/data-analysis/consumption/DATASET%3A0001',
      '/data-analysis/consumption/DATASET%3A1?reviewVersion=0003',
      '/data-analysis/consumption/EXTERNAL%3A1',
      '/data-analysis/consumption/DATA_SERVICE:1',
      '/data-analysis/consumption/DATA_SERVICE%3A1?reviewVersion=3\\path',
    ];
    invalid.forEach((target) => expect(parseConsumptionReviewReturnPath(target)).toBeNull());
    expect(consumptionReviewReturnPath('DATASET', '01', '2')).toBeNull();
    expect(consumptionReviewReturnPath('DATA_SERVICE', '5', '00')).toBeNull();
    const source = consumerSourceTarget(
      consumer('DATA_SERVICE', 'DATA_SERVICE_CONSUMER', '2'), 'https://attacker.example/',
    )!;
    expect(source.href).toBe('/data-service/access?consumerId=2');
    expect(source.href).not.toContain('returnTo');
  });

  it('does not reinterpret a managed caller ID through Number when it is unsafe', () => {
    const path = consumptionReviewReturnPath('DATA_SERVICE', '8', '9007199254740993');
    expect(consumerSourceTarget(consumer('DATA_SERVICE', 'DATA_SERVICE_CONSUMER', '9007199254740993'), path)).toBeNull();
    expect(consumerSourceTarget(consumer('DASHBOARD', 'DASHBOARD', '9007199254740993'), path)?.href)
      .toContain('/dashboard/9007199254740993?');
  });

});
