import {
  buildWorkspaceCreateInput,
  filterWorkspaceAssignmentCandidates,
  toWorkspaceDepartmentTreeData,
} from './workspace';

const user = (id: number, userName: string) => ({ id, userName });

describe('workspace helpers', () => {
  it('hides the virtual department root and preserves hierarchy', () => {
    expect(
      toWorkspaceDepartmentTreeData({
        id: 0,
        childList: [
          {
            id: 10,
            deptName: '数据中心',
            childList: [{ id: 11, deptName: '开发组' }],
          },
        ],
      }),
    ).toEqual([
      {
        value: 10,
        title: '数据中心',
        children: [{ value: 11, title: '开发组' }],
      },
    ]);
  });

  it('makes the workspace creator the initial owner', () => {
    expect(
      buildWorkspaceCreateInput(
        {
          projectName: '  成都一院  ',
          description: ' 数据项目 ',
          deptId: 10,
        },
        7,
      ),
    ).toEqual({
      projectName: '成都一院',
      description: '数据项目',
      deptId: 10,
      ownerIdList: [7],
    });
  });

  it('keeps owner and normal-member assignment mutually exclusive', () => {
    const owner = user(1, 'owner');
    const member = user(2, 'member');
    const free = user(3, 'free');
    const detail = {
      id: 9,
      projectCode: 'p9',
      projectName: '工作空间',
      owners: [owner],
      members: [member],
      owner,
      memberCount: 1,
      status: 'ENABLED' as const,
    };
    const candidates = [owner, member, free];

    expect(
      filterWorkspaceAssignmentCandidates('owner', detail, candidates).map(
        (item) => item.id,
      ),
    ).toEqual([1, 3]);
    expect(
      filterWorkspaceAssignmentCandidates('member', detail, candidates).map(
        (item) => item.id,
      ),
    ).toEqual([2, 3]);
  });
});
