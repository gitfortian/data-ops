import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const serviceRoot = __dirname;
const analysisRoot = join(serviceRoot, '..', '..', 'components', 'analysis');

const productionSources = () => readdirSync(serviceRoot, { withFileTypes: true })
  .filter((entry) => entry.isFile())
  .map((entry) => entry.name)
  .filter((name) => /\.(ts|tsx)$/.test(name) && !name.endsWith('.test.ts') && !name.endsWith('.test.tsx'));

describe('Dataset frontend dependency boundary', () => {
  it('keeps Dataset service code independent from UI component modules', () => {
    productionSources().forEach((name) => {
      const source = readFileSync(join(serviceRoot, name), 'utf8');
      expect(source).not.toMatch(/from\s+['"]@\/components\//);
      expect(source).not.toContain('@/components/analysis/model');
    });
  });

  it('owns Dataset domain and query contracts outside Analysis', () => {
    const datasetModel = readFileSync(join(serviceRoot, 'model.ts'), 'utf8');
    const analysisModel = readFileSync(join(analysisRoot, 'model.ts'), 'utf8');

    expect(datasetModel).toContain('export interface PublishedDataset');
    expect(datasetModel).toContain('export interface DatasetQueryPayload');
    expect(analysisModel).toContain("from '@/services/dataset/model'");
    expect(analysisModel).not.toContain('export interface PublishedDataset');
    expect(analysisModel).not.toContain('export interface DatasetQueryPayload');
  });

  it('does not keep the deprecated Analysis Dataset service shim', () => {
    expect(existsSync(join(analysisRoot, 'dataset-service.ts'))).toBe(false);
  });
});
