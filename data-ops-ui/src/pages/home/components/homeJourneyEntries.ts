import { appRoutes, canAccessNavigationRoute } from '@/config/navigation';

/**
 * FE-A1 presentation-only journeys. Each destination must already exist in
 * navigation.ts and pass its *original* menu-code and permission checks.
 *
 * Do not create a new RBAC parent or task state. A journey is an entry into an
 * owning domain, not evidence that a cross-domain operation has completed.
 */
export type HomeJourneyKey =
  | 'build' | 'consume' | 'recover'
  | 'metric' | 'security' | 'semantic' | 'mdm' | 'agent';

export interface HomeJourneyEntry {
  key: HomeJourneyKey;
  title: string;
  description: string;
  story: string;
  action: string;
  path: string;
  destination: string;
  primary: boolean;
}

interface HomeJourneyDefinition {
  key: HomeJourneyKey;
  title: string;
  description: string;
  story: string;
  primary: boolean;
  candidates: readonly string[];
}

const JOURNEYS: readonly HomeJourneyDefinition[] = [
  {
    key: 'build', title: '建设可信数据',
    description: '接入数据、查看结构，或继续已有建模工作。',
    story: 'GM-J1', primary: true,
    candidates: ['data-source', 'data-metadata-collect', 'modeling-workspace'],
  },
  {
    key: 'consume', title: '使用已有数据',
    description: '查找数据产品，查看版本与使用条件。',
    story: 'GM-J3', primary: true,
    candidates: ['consumption-catalog'],
  },
  {
    key: 'recover', title: '处理运行异常',
    description: '查看质量问题或执行记录，定位异常。',
    story: 'GM-J4', primary: true,
    candidates: ['data-quality-overview', 'data-development-execution', 'workflow-instances'],
  },
  {
    key: 'metric', title: '定义和验证指标',
    description: '查看与管理指标定义。',
    story: 'GM-J2', primary: false, candidates: ['metric-manage'],
  },
  {
    key: 'security', title: '安全与访问',
    description: '查看数据安全与访问情况。',
    story: 'GM-J5', primary: false, candidates: ['data-security-overview'],
  },
  {
    key: 'semantic', title: '业务语义与标准',
    description: '查看业务过程与数据标准。',
    story: 'GM-J1/J2', primary: false,
    candidates: ['semantic-processes', 'semantic-standards'],
  },
  {
    key: 'mdm', title: '主数据管理',
    description: '查看和管理主数据。',
    story: 'GM-MDM', primary: false, candidates: ['mdm-overview'],
  },
  {
    key: 'agent', title: '智能助手',
    description: '通过对话获取数据工作建议。',
    story: 'GM-J1/J6', primary: false, candidates: ['ai-agent'],
  },
];

export function resolveHomeJourneyEntries(
  permissionCodes: readonly string[] | null | undefined,
  menuCodes: readonly string[] | null | undefined,
  options: { agentEnabled?: boolean } = {},
): HomeJourneyEntry[] {
  return JOURNEYS.flatMap((journey): HomeJourneyEntry[] => {
    if (journey.key === 'agent' && !options.agentEnabled) return [];
    const route = journey.candidates
      .map((id) => appRoutes.find((candidate) => candidate.id === id))
      .find((candidate) => candidate
        && canAccessNavigationRoute(candidate, permissionCodes, menuCodes));
    if (!route) return [];
    return [{
      key: journey.key,
      title: journey.title,
      description: journey.description,
      story: journey.story,
      primary: journey.primary,
      action: `前往${route.title}`,
      path: route.path,
      destination: route.title,
    }];
  });
}
