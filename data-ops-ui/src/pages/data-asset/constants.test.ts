import { sourceObjectPath } from './constants';

describe('asset source object navigation', () => {
  it('uses stable Dataset identity to return to Data Development provenance', () => {
    expect(sourceObjectPath('DATASET', '55', 101)).toBe(
      '/data-development?datasetId=55&returnAssetId=101',
    );
  });

  it('keeps model source navigation and return context intact', () => {
    expect(sourceObjectPath('MODEL', '8', 101)).toBe(
      '/modeling/models/8?returnAssetId=101',
    );
  });
});
