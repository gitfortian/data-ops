import { history } from '@umijs/max';
import { useEffect } from 'react';

/**
 * 数据开发暂时只保留左侧资源树。
 * 专业节点编辑将在首页右侧工作区重建，不再使用独立详情页。
 */
export default function DataDevelopmentTaskPage() {
  useEffect(() => {
    history.replace('/data-development');
  }, []);

  return null;
}
