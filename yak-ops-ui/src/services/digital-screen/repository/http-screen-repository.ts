import { resolveScreenTemplateById } from '@/services/screen-template-service';
import HttpUtils from '@/utils/HttpUtils';
import type {
  CreateDigitalScreenInput,
  DigitalScreenBindings,
  DigitalScreenInstance,
  DigitalScreenStatus,
  DigitalScreenVersion,
  DigitalScreenVersionSummary,
  UpdateDigitalScreenInput,
} from '../types';
import type { ScreenPublicationRepository } from './screen-publication-repository';
import type { ScreenRepository } from './screen-repository';

const DIGITAL_SCREEN_API = '/api/v1/digital-screens';

interface DigitalScreenWire {
  id: string;
  name: string;
  description?: string | null;
  templateId: string;
  templateVersion: 1;
  status: DigitalScreenStatus;
  bindings?: DigitalScreenBindings | null;
  revision: number;
  publishedRevision?: number | null;
  publishedVersionNo?: number | null;
  hasUnpublishedChanges: boolean;
  publishedTime?: string | null;
  createTime: string;
  updateTime: string;
}

interface DigitalScreenVersionSummaryWire {
  id: string;
  versionNo: number;
  sourceRevision: number;
  name: string;
  publishedTime: string;
  current: boolean;
}

interface DigitalScreenVersionWire {
  id: string;
  screenId: string;
  versionNo: number;
  sourceRevision: number;
  name: string;
  description?: string | null;
  templateId: string;
  templateVersion: 1;
  bindings?: DigitalScreenBindings | null;
  publishedTime: string;
  createTime: string;
}

const toInstance = (wire: DigitalScreenWire): DigitalScreenInstance => ({
  id: String(wire.id),
  name: wire.name,
  description: wire.description || undefined,
  templateId: wire.templateId,
  templateVersion: wire.templateVersion,
  status: wire.status,
  bindings: wire.bindings ?? {},
  revision: wire.revision,
  publishedRevision: wire.publishedRevision ?? undefined,
  publishedVersionNo: wire.publishedVersionNo ?? undefined,
  hasUnpublishedChanges: wire.hasUnpublishedChanges,
  publishedAt: wire.publishedTime || undefined,
  createdAt: wire.createTime,
  updatedAt: wire.updateTime,
});

const toVersion = (wire: DigitalScreenVersionWire): DigitalScreenVersion => ({
  id: String(wire.id),
  screenId: String(wire.screenId),
  versionNo: wire.versionNo,
  sourceRevision: wire.sourceRevision,
  name: wire.name,
  description: wire.description || undefined,
  templateId: wire.templateId,
  templateVersion: wire.templateVersion,
  bindings: wire.bindings ?? {},
  publishedAt: wire.publishedTime,
  createdAt: wire.createTime,
});

const toPublishedInstance = (wire: DigitalScreenVersionWire): DigitalScreenInstance => ({
  id: String(wire.screenId),
  name: wire.name,
  description: wire.description || undefined,
  templateId: wire.templateId,
  templateVersion: wire.templateVersion,
  status: 'published',
  bindings: wire.bindings ?? {},
  revision: wire.sourceRevision,
  publishedRevision: wire.sourceRevision,
  publishedVersionNo: wire.versionNo,
  hasUnpublishedChanges: false,
  publishedAt: wire.publishedTime,
  createdAt: wire.createTime,
  updatedAt: wire.publishedTime,
});

class HttpScreenRepository implements ScreenRepository, ScreenPublicationRepository {
  async list() {
    const values = await HttpUtils.getData<DigitalScreenWire[]>(DIGITAL_SCREEN_API);
    return (values || []).map(toInstance);
  }

  async get(id: string) {
    return toInstance(await HttpUtils.getData<DigitalScreenWire>(`${DIGITAL_SCREEN_API}/${id}`));
  }

  async getPublished(id: string) {
    return toPublishedInstance(await HttpUtils.getData<DigitalScreenVersionWire>(
      `${DIGITAL_SCREEN_API}/${id}/published`,
    ));
  }

  async listVersions(id: string) {
    const values = await HttpUtils.getData<DigitalScreenVersionSummaryWire[]>(
      `${DIGITAL_SCREEN_API}/${id}/versions`,
    );
    return (values || []).map((wire): DigitalScreenVersionSummary => ({
      id: String(wire.id),
      versionNo: wire.versionNo,
      sourceRevision: wire.sourceRevision,
      name: wire.name,
      publishedAt: wire.publishedTime,
      current: wire.current,
    }));
  }

  async getVersion(id: string, versionNo: number) {
    return toVersion(await HttpUtils.getData<DigitalScreenVersionWire>(
      `${DIGITAL_SCREEN_API}/${id}/versions/${versionNo}`,
    ));
  }

  async create(input: CreateDigitalScreenInput) {
    const name = input.name.trim();
    if (!name) throw new Error('请输入大屏名称');
    if (!resolveScreenTemplateById(input.templateId)) throw new Error('所选大屏模板不存在');

    return toInstance(await HttpUtils.postData<DigitalScreenWire>(DIGITAL_SCREEN_API, {
      name,
      description: input.description?.trim() || undefined,
      templateId: input.templateId,
      bindings: input.bindings ?? {},
    }));
  }

  async update(id: string, input: UpdateDigitalScreenInput) {
    const payload: Record<string, unknown> = {};
    if (input.name !== undefined) {
      const name = input.name.trim();
      if (!name) throw new Error('请输入大屏名称');
      payload.name = name;
    }
    if (input.description !== undefined) payload.description = input.description.trim();
    if (input.bindings !== undefined) payload.bindings = input.bindings;

    return toInstance(await HttpUtils.putData<DigitalScreenWire>(
      `${DIGITAL_SCREEN_API}/${id}`,
      payload,
    ));
  }

  async publish(id: string) {
    return toInstance(await HttpUtils.postData<DigitalScreenWire>(
      `${DIGITAL_SCREEN_API}/${id}/publish`,
    ));
  }

  async unpublish(id: string) {
    return toInstance(await HttpUtils.postData<DigitalScreenWire>(
      `${DIGITAL_SCREEN_API}/${id}/offline`,
    ));
  }

  async rollback(id: string, versionNo: number) {
    return toInstance(await HttpUtils.postData<DigitalScreenWire>(
      `${DIGITAL_SCREEN_API}/${id}/versions/${versionNo}/rollback`,
    ));
  }

  async duplicate(id: string) {
    return toInstance(await HttpUtils.postData<DigitalScreenWire>(
      `${DIGITAL_SCREEN_API}/${id}/duplicate`,
    ));
  }

  async remove(id: string) {
    await HttpUtils.deleteData<boolean>(`${DIGITAL_SCREEN_API}/${id}`);
  }
}

export const httpScreenRepository: ScreenRepository & ScreenPublicationRepository = new HttpScreenRepository();
