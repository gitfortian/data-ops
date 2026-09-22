import type {
  CreateDigitalScreenInput,
  DigitalScreenInstance,
  UpdateDigitalScreenInput,
} from '../types';

/** Persistence port for Digital Screen definitions. */
export interface ScreenRepository {
  list(): Promise<DigitalScreenInstance[]>;
  get(id: string): Promise<DigitalScreenInstance>;
  create(input: CreateDigitalScreenInput): Promise<DigitalScreenInstance>;
  update(id: string, input: UpdateDigitalScreenInput): Promise<DigitalScreenInstance>;
  publish(id: string): Promise<DigitalScreenInstance>;
  unpublish(id: string): Promise<DigitalScreenInstance>;
  duplicate(id: string): Promise<DigitalScreenInstance>;
  remove(id: string): Promise<void>;
}
