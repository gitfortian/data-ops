import { toCurrentUser } from './currentIdentity';

describe('current identity normalization', () => {
  it('fails closed when optional authorization context is absent', () => {
    const user = toCurrentUser({ id: 7, userName: 'yak' });

    expect(user).toMatchObject({
      id: 7,
      userName: 'yak',
      name: 'yak',
      userid: '7',
      deptId: null,
      roleList: [],
      permissionCodes: [],
      projectList: [],
    });
    expect(user.menuCodes).toBeUndefined();
  });

  it('preserves the complete root identity supplied by current-account', () => {
    const user = toCurrentUser({
      id: 8,
      userName: 'root',
      realName: ' 系统管理员 ',
      deptId: 12,
      roleList: [{ id: 2, roleName: '系统管理员' }],
      permissionCodes: ['security:root'],
      menuCodes: ['system', 'system-security-projects'],
      projectList: [
        { id: 3, projectCode: 'DEFAULT', projectName: '默认空间' },
        { id: 4, projectCode: 'DATA', projectName: '数据空间' },
      ],
    });

    expect(user).toMatchObject({
      name: '系统管理员',
      deptId: 12,
      roleList: [{ id: 2, roleName: '系统管理员' }],
      permissionCodes: ['security:root'],
      menuCodes: ['system', 'system-security-projects'],
      projectList: [
        { id: 3, projectCode: 'DEFAULT' },
        { id: 4, projectCode: 'DATA' },
      ],
    });
  });
});
