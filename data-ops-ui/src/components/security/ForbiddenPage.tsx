import { history } from '@umijs/max';
import { Button, Result } from 'antd';

export default function ForbiddenPage() {
  return (
    <Result
      status="403"
      title="无权访问此页面"
      subTitle="当前账号没有此页面的访问权限。可返回首页，或联系空间管理员核对权限。"
      extra={
        <Button type="primary" onClick={() => history.push('/home')}>
          返回首页
        </Button>
      }
    />
  );
}
