import { fireEvent, render, screen, within } from '@testing-library/react';
import type { ComponentProps } from 'react';
import type { ReactFlowProps } from 'reactflow';
import { logicalSample } from '../../../../tests/fixtures/modelingLogical';
import LogicalDesignStudio from './LogicalDesignStudio';

jest.mock('reactflow/dist/style.css', () => ({}));
// Only replace the browser's SVG/measurement boundary; real object cards,
// navigation, inspector, tables and callbacks still exercise the feature.
jest.mock('reactflow', () => {
  const ReactLib = require('react');
  return {
    __esModule: true,
    default: ({ nodes = [], edges = [], nodeTypes = {}, onNodeClick, onEdgeClick, children }: ReactFlowProps) => <div>
      {nodes.map(node => <div key={node.id} onClick={event => onNodeClick?.(event, node)}>
        {ReactLib.createElement(nodeTypes[node.type || 'logicalEntity'], { data: node.data, selected: node.selected })}
      </div>)}
      {edges.map(edge => <button key={edge.id} type="button" aria-label={`选择关系 ${edge.id}`}
        onClick={event => onEdgeClick?.(event, edge)}>{edge.label}</button>)}{children}
    </div>,
    useNodesState: (initial: unknown[]) => {
      const [nodes, setNodes] = ReactLib.useState(initial);
      return [nodes, setNodes, jest.fn()];
    },
    Handle: () => null, Background: () => null, Controls: () => null,
    MarkerType: { ArrowClosed: 'arrowclosed' }, Position: { Left: 'left', Right: 'right' },
  };
});

const props = (extra: Partial<ComponentProps<typeof LogicalDesignStudio>> = {}): ComponentProps<typeof LogicalDesignStudio> => ({
  detail: logicalSample(), standards: [], canEdit: true, busy: false,
  onEditEntity: jest.fn(), onEditAttribute: jest.fn(), onEditRelation: jest.fn(), ...extra,
});

it('locates an attribute from search and edits the same owner object in the inspector', async () => {
  const input = props();
  render(<LogicalDesignStudio {...input} />);
  const nav = within(screen.getByLabelText('逻辑对象导航'));
  fireEvent.change(nav.getByPlaceholderText('查找实体或属性'), { target: { value: 'amount' } });
  fireEvent.click(nav.getByLabelText('定位属性 order.amount'));
  const inspector = within(screen.getByLabelText('逻辑对象属性'));
  expect(inspector.getByLabelText('当前逻辑属性')).toHaveTextContent('实付金额');
  fireEvent.click(inspector.getByText('编辑属性'));
  expect(input.onEditAttribute).toHaveBeenCalledWith(11, 102);
  expect(screen.getByLabelText('逻辑实体关系画布')).toHaveTextContent('活动');
});

it('inspects the actual relation endpoints and preserves unknown cardinality until explicit editing', async () => {
  const input = props();
  render(<LogicalDesignStudio {...input} />);
  fireEvent.click(await screen.findByLabelText('选择关系 51'));
  const inspector = within(screen.getByLabelText('逻辑对象属性'));
  expect(inspector.getByText('订单 → 客户')).toBeInTheDocument();
  expect(inspector.getByText('基数待确认')).toBeInTheDocument();
  expect(input.onEditRelation).not.toHaveBeenCalled();
  fireEvent.click(inspector.getByText('编辑关系'));
  expect(input.onEditRelation).toHaveBeenCalledWith(51);
});

it('uses the same selection and refreshed attributes across the canvas and table views', () => {
  const input = props();
  const view = render(<LogicalDesignStudio {...input} />);
  fireEvent.click(screen.getByRole('button', { name: '选择实体 订单' }));
  fireEvent.click(screen.getByText('表格设计'));
  const updated = logicalSample(); updated.entities[0].attributes[1].name = '本次保存的金额';
  view.rerender(<LogicalDesignStudio {...input} detail={updated} />);
  const inspector = within(screen.getByLabelText('逻辑对象属性'));
  expect(inspector.getByText('本次保存的金额')).toBeInTheDocument();
  fireEvent.click(inspector.getByLabelText('查看属性 order.amount'));
  fireEvent.click(inspector.getByText('编辑属性'));
  expect(input.onEditAttribute).toHaveBeenCalledWith(11, 102);
});

it('keeps layout and neighborhood actions local while preserving read-only inspection', () => {
  const input = props({ canEdit: false });
  render(<LogicalDesignStudio {...input} />);
  fireEvent.click(screen.getByRole('button', { name: '选择实体 订单' }));
  fireEvent.click(screen.getByText('聚焦关联'));
  expect(screen.getByLabelText('逻辑实体关系画布')).not.toHaveTextContent('活动');
  fireEvent.click(screen.getByText('自动排列'));
  fireEvent.click(screen.getByText('显示全部'));
  expect(screen.getByLabelText('逻辑实体关系画布')).toHaveTextContent('活动');
  expect(screen.getByRole('button', { name: '编辑实体' })).toBeDisabled();
  expect(input.onEditEntity).not.toHaveBeenCalled();
  expect(input.onEditAttribute).not.toHaveBeenCalled();
  expect(input.onEditRelation).not.toHaveBeenCalled();
});

it('exposes the canvas budget instead of silently dropping entities and permits a bounded neighborhood', () => {
  const detail = logicalSample();
  for (let id = 1000; id < 1040; id++) detail.entities.push({
    entity: { id, logicalModelId: 10, code: `entity_${id}`, name: `实体 ${id}` }, attributes: [],
  });
  render(<LogicalDesignStudio {...props({ detail })} />);
  expect(screen.getByText(/当前有 43 个实体，建议使用表格设计/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '选择实体 实体 1039' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '选择实体 订单' }));
  fireEvent.click(screen.getByText('聚焦关联'));
  expect(screen.queryByText(/建议使用表格设计/)).not.toBeInTheDocument();
  expect(screen.getByLabelText('逻辑实体关系画布')).toHaveTextContent('客户');
});

it('carries a large entity and attribute identity unchanged from a card to the edit callback', async () => {
  const detail = logicalSample();
  detail.entities = [detail.entities[0]]; detail.relations = [];
  // Simulate a string Java Long at the API boundary without changing the owner API types.
  detail.entities[0].entity.id = '2109169776643895297' as unknown as number;
  detail.entities[0].attributes[1].id = '2109169776643895298' as unknown as number;
  detail.entities[0].attributes[1].entityId = '2109169776643895297' as unknown as number;
  const input = props({ detail });
  render(<LogicalDesignStudio {...input} />);
  fireEvent.click(await within(screen.getByLabelText('逻辑实体关系画布')).findByLabelText('定位属性 order.amount'));
  fireEvent.click(screen.getByRole('button', { name: '编辑属性' }));
  expect(input.onEditAttribute).toHaveBeenCalledWith('2109169776643895297', '2109169776643895298');
});

it('opens the first entity and edits an attribute directly from its list', () => {
  const input = props();
  render(<LogicalDesignStudio {...input} />);
  const inspector = within(screen.getByLabelText('逻辑对象属性'));
  expect(inspector.getByText('订单')).toBeInTheDocument();
  fireEvent.click(inspector.getByRole('button', { name: '编辑属性 order.amount' }));
  expect(input.onEditAttribute).toHaveBeenCalledWith(11, 102);
});

it('keeps empty design and entity states actionable', () => {
  const detail = logicalSample(); detail.entities = []; detail.relations = [];
  const input = props({ detail });
  const view = render(<LogicalDesignStudio {...input} />);
  fireEvent.click(screen.getByRole('button', { name: '创建第一个实体' }));
  expect(input.onEditEntity).toHaveBeenCalledWith();
  const next = logicalSample(); next.model.id = 20;
  next.entities = [next.entities[1]]; next.relations = [];
  view.rerender(<LogicalDesignStudio {...input} detail={next} />);
  fireEvent.click(screen.getByRole('button', { name: '添加第一个属性' }));
  expect(input.onEditAttribute).toHaveBeenCalledWith(12);
});

it('resets navigation and selection when switching to another model', () => {
  const input = props();
  const view = render(<LogicalDesignStudio {...input} />);
  fireEvent.change(screen.getByPlaceholderText('查找实体或属性'), { target: { value: 'amount' } });
  fireEvent.click(screen.getByText('聚焦关联'));
  const next = logicalSample(); next.model.id = 20;
  next.entities = [next.entities[2]]; next.relations = [];
  view.rerender(<LogicalDesignStudio {...input} detail={next} />);
  expect(screen.getByPlaceholderText('查找实体或属性')).toHaveValue('');
  expect(within(screen.getByLabelText('逻辑对象属性')).getByText('活动')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '显示全部' })).not.toBeInTheDocument();
});
