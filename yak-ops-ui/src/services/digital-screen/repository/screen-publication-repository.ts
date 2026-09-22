import type {
  DigitalScreenInstance,
  DigitalScreenVersion,
  DigitalScreenVersionSummary,
} from '../types';

/** Read/history port for immutable Digital Screen publication snapshots. */
export interface ScreenPublicationRepository {
  getPublished(id: string): Promise<DigitalScreenInstance>;
  listVersions(id: string): Promise<DigitalScreenVersionSummary[]>;
  getVersion(id: string, versionNo: number): Promise<DigitalScreenVersion>;
  rollback(id: string, versionNo: number): Promise<DigitalScreenInstance>;
}
