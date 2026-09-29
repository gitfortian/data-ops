import {
  Descriptions,
  Drawer,
  Space,
  Spin,
  Tag,
  message,
} from 'antd';
import {
  forwardRef,
  useCallback,
  useImperativeHandle,
  useState,
} from 'react';

import { YakEmpty } from '@/components/ui';
import {
  getUserDetail,
  type SystemUser,
} from '@/services/security/users';

import { getSystemErrorMessage } from '../../utils';

export interface UserDetailDrawerRef {
  open: (user: SystemUser) => Promise<void>;
}

const UserDetailDrawer = forwardRef<UserDetailDrawerRef>((_, ref) => {
  const [open, setOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [detail, setDetail] = useState<SystemUser>();

  const show = useCallback(async (row: SystemUser) => {
    setOpen(true);
    setIsLoading(true);
    setDetail(undefined);

    try {
      setDetail(await getUserDetail(row.id));
    } catch (error) {
      message.error(
        getSystemErrorMessage(error, '用户详情加载失败'),
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useImperativeHandle(ref, () => ({ open: show }), [show]);

  const close = () => {
    setOpen(false);
    setDetail(undefined);
  };

  return (
    <Drawer
      open={open}
      title="用户详情"
      width={640}
      destroyOnClose
      onClose={close}
    >
      <Spin spinning={isLoading}>
        {detail ? (
          <Descriptions bordered column={1}>
            <Descriptions.Item label="用户 ID">{detail.id}</Descriptions.Item>
            <Descriptions.Item label="用户名">{detail.userName}</Descriptions.Item>
            <Descriptions.Item label="真实姓名">{detail.realName || '-'}</Descriptions.Item>
            <Descriptions.Item label="手机号">{detail.phone || '-'}</Descriptions.Item>
            <Descriptions.Item label="邮箱">{detail.email || '-'}</Descriptions.Item>
            <Descriptions.Item label="角色">
              {detail.roleList?.length ? (
                <Space wrap size={[4, 4]}>
                  {detail.roleList.map((role) => (
                    <Tag key={role.id}>{role.roleName}</Tag>
                  ))}
                </Space>
              ) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="授权项目">
              {detail.projectList?.length ? (
                <Space wrap size={[4, 4]}>
                  {detail.projectList.map((project) => (
                    <Tag key={project.id}>
                      {project.projectName || project.projectCode || project.id}
                    </Tag>
                  ))}
                </Space>
              ) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{detail.createTime || '-'}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{detail.updateTime || '-'}</Descriptions.Item>
          </Descriptions>
        ) : (
          !isLoading && <YakEmpty compact title="暂无用户详情" />
        )}
      </Spin>
    </Drawer>
  );
});

UserDetailDrawer.displayName = 'UserDetailDrawer';
export default UserDetailDrawer;
