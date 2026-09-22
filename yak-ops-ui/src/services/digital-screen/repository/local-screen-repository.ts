import { resolveScreenTemplateById } from '@/services/screen-template-service';
import type {
  CreateDigitalScreenInput,
  DigitalScreenBindings,
  DigitalScreenInstance,
  UpdateDigitalScreenInput,
} from '../types';
import type { ScreenRepository } from './screen-repository';

const STORAGE_KEY = 'yak-ops:digital-screens:v1';

const now = () => new Date().toISOString();

const createId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `screen-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
};

const normalizeBindings = (value: unknown): DigitalScreenBindings => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
  return value as DigitalScreenBindings;
};

const readScreens = (): DigitalScreenInstance[] => {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.flatMap((item) => {
      if (!item || typeof item !== 'object') return [];
      const candidate = item as Partial<DigitalScreenInstance>;
      if (
        typeof candidate.id !== 'string'
        || typeof candidate.name !== 'string'
        || typeof candidate.templateId !== 'string'
      ) return [];
      return [{
        ...candidate,
        bindings: normalizeBindings(candidate.bindings),
      } as DigitalScreenInstance];
    });
  } catch {
    return [];
  }
};

const writeScreens = (screens: DigitalScreenInstance[]) => {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(screens));
};

const sortScreens = (screens: DigitalScreenInstance[]) => [...screens].sort(
  (left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime(),
);

class LocalScreenRepository implements ScreenRepository {
  async list() {
    return sortScreens(readScreens());
  }

  async get(id: string) {
    const screen = readScreens().find((item) => item.id === id);
    if (!screen) throw new Error('数字化大屏不存在或已被删除');
    return screen;
  }

  async create(input: CreateDigitalScreenInput) {
    const name = input.name.trim();
    if (!name) throw new Error('请输入大屏名称');
    if (!resolveScreenTemplateById(input.templateId)) throw new Error('所选大屏模板不存在');

    const timestamp = now();
    const screen: DigitalScreenInstance = {
      id: createId(),
      name,
      description: input.description?.trim() || undefined,
      templateId: input.templateId,
      templateVersion: 1,
      status: 'draft',
      bindings: input.bindings ?? {},
      createdAt: timestamp,
      updatedAt: timestamp,
    };

    writeScreens([screen, ...readScreens()]);
    return screen;
  }

  async update(id: string, input: UpdateDigitalScreenInput) {
    let updated: DigitalScreenInstance | undefined;
    const screens = readScreens().map((screen): DigitalScreenInstance => {
      if (screen.id !== id) return screen;
      const name = input.name === undefined ? screen.name : input.name.trim();
      if (!name) throw new Error('请输入大屏名称');
      const next: DigitalScreenInstance = {
        ...screen,
        name,
        description: input.description === undefined
          ? screen.description
          : input.description.trim() || undefined,
        bindings: input.bindings === undefined ? screen.bindings : input.bindings,
        updatedAt: now(),
      };
      updated = next;
      return next;
    });

    if (!updated) throw new Error('数字化大屏不存在或已被删除');
    writeScreens(screens);
    return updated;
  }

  async publish(id: string) {
    let published: DigitalScreenInstance | undefined;
    const timestamp = now();
    const screens = readScreens().map((screen): DigitalScreenInstance => {
      if (screen.id !== id) return screen;
      const next: DigitalScreenInstance = {
        ...screen,
        status: 'published',
        publishedAt: timestamp,
        updatedAt: timestamp,
      };
      published = next;
      return next;
    });

    if (!published) throw new Error('数字化大屏不存在或已被删除');
    writeScreens(screens);
    return published;
  }

  async unpublish(id: string) {
    let draft: DigitalScreenInstance | undefined;
    const screens = readScreens().map((screen): DigitalScreenInstance => {
      if (screen.id !== id) return screen;
      const next: DigitalScreenInstance = {
        ...screen,
        status: 'draft',
        publishedAt: undefined,
        updatedAt: now(),
      };
      draft = next;
      return next;
    });

    if (!draft) throw new Error('数字化大屏不存在或已被删除');
    writeScreens(screens);
    return draft;
  }

  async duplicate(id: string) {
    const source = await this.get(id);
    return this.create({
      name: `${source.name} - 副本`,
      description: source.description,
      templateId: source.templateId,
      bindings: source.bindings,
    });
  }

  async remove(id: string) {
    const screens = readScreens();
    const next = screens.filter((screen) => screen.id !== id);
    if (next.length === screens.length) throw new Error('数字化大屏不存在或已被删除');
    writeScreens(next);
  }
}

export const localScreenRepository: ScreenRepository = new LocalScreenRepository();
