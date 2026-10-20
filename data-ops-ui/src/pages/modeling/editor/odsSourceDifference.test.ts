import { compareOdsSourceColumns } from './odsSourceDifference';

it('distinguishes source-only, model-only and generated technical fields', () => {
  const difference = compareOdsSourceColumns([
    { name: 'order_id', typeName: 'BIGINT' }, { name: 'etl_time', typeName: 'TIMESTAMP' },
    { name: 'process_time', typeName: 'TIMESTAMP', technical: true },
  ], [
    { columnName: 'order_id', dataType: 'BIGINT' }, { columnName: 'custom_flag', dataType: 'VARCHAR' },
    { columnName: 'process_time', dataType: 'TIMESTAMP' },
  ], []);
  expect(difference.sourceOnly).toEqual(['etl_time']);
  expect(difference.modelOnly).toEqual(['custom_flag']);
  expect(difference.byTargetName.get('process_time')?.kind).toBe('technical');
  expect(difference.byTargetName.get('order_id')?.kind).toBe('same');
});

it('reports the actual changed attributes without guessing a renamed-field mapping', () => {
  const difference = compareOdsSourceColumns([
    { name: 'order_id', typeName: 'BIGINT', primaryKey: true },
    { name: 'order_no', typeName: 'VARCHAR', size: 64, nullable: true },
  ], [
    { columnName: 'order_key', dataType: 'BIGINT' },
    { columnName: 'order_no', dataType: 'CHAR', length: 32, nullable: false },
  ], []);
  expect(difference.sourceOnly).toEqual(['order_id']);
  expect(difference.modelOnly).toEqual(['order_key']);
  expect(difference.changed).toEqual(['order_no']);
  expect(difference.byTargetName.get('order_no')?.details).toEqual([
    '类型：VARCHAR → CHAR', '长度：64 → 32', '可空设置不同',
  ]);
});

it('normalizes type case and unspecified numeric widths while comparing key/default/comment changes', () => {
  const difference = compareOdsSourceColumns([
    { name: 'order_id', typeName: 'bigint', size: 0, scale: 0, primaryKey: true },
    { name: 'amount', typeName: 'DECIMAL', defaultValue: '0', remarks: '金额' },
  ], [
    { columnName: 'order_id', dataType: 'BIGINT', length: null, scale: null },
    { columnName: 'amount', dataType: 'DECIMAL', defaultValue: '1', comment: '实付金额' },
  ], []);
  expect(difference.byTargetName.get('order_id')?.details).toEqual(['主键设置不同']);
  expect(difference.byTargetName.get('amount')?.details).toEqual(['默认值不同', '注释不同']);
});
