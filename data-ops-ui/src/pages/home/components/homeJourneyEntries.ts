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
    description: '从有权限的来源开始，核对表结构、建立 ODS，再继续业务建模。',
    story: 'GM-J1', primary: true,
    candidates: ['data-source', 'data-metadata-collect', 'modeling-workspace'],
  },
  {
    key: 'consume', title: '使用已有数据',
    description: '先看数据产品及其授权和版本；无需重走数据建设。',
    story: 'GM-J3', primary: true,
    candidates: ['consumption-catalog', 'dataset-management', 'data-service-api'],
  },
  {
    key: 'recover', title: '处理运行异常',
    description: '定位真实质量或执行记录，再进入有权限的专业恢复流程。',
    story: 'GM-J4', primary: true,
    candidates: ['data-quality-overview', 'data-development-execution', 'workflow-instances'],
  },
  {
    key: 'metric', title: '定义和验证指标',
    description: '复用业务语义、指标版本、校验和发布入口。',
    story: 'GM-J2', primary: false, candidates: ['metric-manage'],
  },
  {
    key: 'security', title: '安全与访问',
    description: '核对分类、策略、授权和访问审计。',
    story: 'GM-J5', primary: false, candidates: ['data-security-overview'],
  },
  {
    key: 'semantic', title: '业务语义与标准',
    description: '核对业务过程、粒度及合法标准字段。',
    story: 'GM-J1/J2', primary: false,
    candidates: ['semantic-processes', 'semantic-standards'],
  },
  {
    key: 'mdm', title: '主数据管理',
    description: '进入主数据的识别、审查和发布专业工作台。',
    story: 'GM-MDM', primary: false, candidates: ['mdm-overview'],
  },
  {
    key: 'agent', title: '智能助手',
    description: '进入现有助手。建议与问答仍须原域授权和真实证据。',
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
