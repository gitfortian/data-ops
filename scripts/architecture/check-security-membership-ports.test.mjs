import test from 'node:test';
import assert from 'node:assert/strict';
import { readRepository, validateMembershipPorts } from './check-security-membership-ports.mjs';

test('A8.2g actual repository uses Platform ports with intact legacy DAO facades', () => {
  assert.deepEqual(validateMembershipPorts(readRepository()), []);
});
test('A8.2g blocks missing owner-type selection from Project port', () => {
  const files = readRepository();
  files.project = files.project.replace('selectUserIdListByProjectId(', 'selectMembers(');
  assert.match(validateMembershipPorts(files).join('\n'), /missing method selectUserIdListByProjectId/);
});
test('A8.2g blocks DTO or database PO leaking into the Platform', () => {
  const files = readRepository();
  files.project += '\nimport io.yak.framework.security.common.po.UserProjectPO;\n';
  assert.match(validateMembershipPorts(files).join('\n'), /may not use legacy DTO\/PO/);
});
test('A8.2g blocks Project DAO abandoning Platform contract', () => {
  const files = readRepository();
  files.projectDao = files.projectDao.replace('extends UserProjectMembershipPort', '');
  assert.match(validateMembershipPorts(files).join('\n'), /Legacy UserProjectDao/);
});
test('A8.2g blocks UserRole DAO abandoning Platform contract', () => {
  const files = readRepository();
  files.roleDao = files.roleDao.replace('extends UserRoleAssignmentPort', '');
  assert.match(validateMembershipPorts(files).join('\n'), /Legacy UserRoleDao/);
});
test('A8.2g blocks a second DAO replacing original Spring MyBatis bean', () => {
  const files = readRepository();
  files.projectImpl = files.projectImpl.replace('implements UserProjectDao', 'implements UserProjectMembershipPort');
  assert.match(validateMembershipPorts(files).join('\n'), /original Spring MyBatis bean/);
});
test('A8.2g blocks a separate Platform instance in Project membership service', () => {
  const files = readRepository();
  files.projectService = files.projectService.replace('this.membershipPort = userProjectDao;',
    'this.membershipPort = anotherProjectPort;');
  assert.match(validateMembershipPorts(files).join('\n'), /identical legacy DAO/);
});
test('A8.2g blocks Role service dropping PO-dependent read through old DAO', () => {
  const files = readRepository();
  files.roleService = files.roleService.replace('assignmentPort.selectAssignmentsByRoleIds(', 'userRoleDao.selectByRoleIds(');
  assert.match(validateMembershipPorts(files).join('\n'), /PO-free read projections/);
});
test('A8.2g blocks Project service losing its dedicated transaction manager', () => {
  const files = readRepository();
  files.projectService = files.projectService.replaceAll('yakSecurityTransactionManager', 'otherTransactionManager');
  assert.match(validateMembershipPorts(files).join('\n'), /Security transactions/);
});

test('A8.2 consolidation rejects redundant legacy DAO state in port-backed services', () => {
  const files = readRepository();
  files.projectService = files.projectService.replace('private final UserProjectMembershipPort membershipPort;',
    'private final UserProjectMembershipPort membershipPort;\\n  private final UserProjectDao userProjectDao;');
  assert.match(validateMembershipPorts(files).join('\\n'), /identical legacy DAO/);
  const roles = readRepository();
  roles.roleService = roles.roleService.replace('private final UserRoleAssignmentPort assignmentPort;',
    'private final UserRoleAssignmentPort assignmentPort;\\n  private final UserRoleDao userRoleDao;');
  assert.match(validateMembershipPorts(roles).join('\\n'), /identical legacy DAO/);
});
