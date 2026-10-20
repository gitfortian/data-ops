/** Task links within the existing, authorized Modeling workspace menu. */
export const MODELING_NAVIGATION_ITEMS = [
  { id: 'modeling-overview', title: '建模总览', path: '/modeling',
    description: '查看数仓五层设计与业务过程覆盖，选择下一步建模工作' },
  { id: 'modeling-logical', title: '逻辑模型', path: '/modeling/logical',
    description: '围绕业务过程设计实体、属性和关系，核对逻辑设计依据' },
  { id: 'modeling-physical', title: '物理模型', path: '/modeling?view=list',
    description: '按 ODS、DIM、DWD、DWS、ADS 管理物理表，维护结构、映射与版本' },
  { id: 'modeling-coverage', title: '过程覆盖', path: '/modeling/mainline',
    description: '按业务过程核对各层模型覆盖，定位缺失层与已有模型' },
  { id: 'modeling-impact', title: '变更影响', path: '/modeling/impact',
    description: '从标准字段或来源字段查询受影响模型，人工核对后继续处理' },
] as const;

export const MODELING_NAVIGATION_OWNER = 'modeling-workspace';

/** URL classification only; the caller must first resolve the authorized route. */
export function getModelingNavigationItem(pathname: string, search = '') {
  const path = pathname.replace(/\/+$/, '') || '/';
  let id: (typeof MODELING_NAVIGATION_ITEMS)[number]['id'] | undefined;
  if (path === '/modeling') {
    const query = new URLSearchParams(search);
    id = query.get('view') === 'list' || query.get('sourceJourney') === 'ods'
      ? 'modeling-physical' : 'modeling-overview';
  } else if (path === '/modeling/logical') id = 'modeling-logical';
  else if (path === '/modeling/mainline') id = 'modeling-coverage';
  else if (path === '/modeling/impact') id = 'modeling-impact';
  else if (/^\/modeling\/models\/[^/]+(?:\/(?:mapping|layer-mapping))?$/.test(path)) id = 'modeling-physical';
  return MODELING_NAVIGATION_ITEMS.find(item => item.id === id);
}
