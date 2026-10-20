import { Outlet } from '@umijs/max';
import { ConfigProvider } from 'antd';
import type { ReactNode } from 'react';
import './modeling.less';

/** Presentation scope only. Project identity, permissions and commands stay with their owners. */
export default function ModelingLayout({ children }: { children?: ReactNode }) {
  return <ConfigProvider
    theme={{ components: {
      Card: { headerFontSize: 14, headerHeight: 56, paddingLG: 20 },
      Form: { itemMarginBottom: 20, labelFontSize: 13, verticalLabelPadding: '0 0 8px' },
      Table: { cellPaddingBlock: 14, cellPaddingInline: 16, cellPaddingBlockSM: 10,
        cellPaddingInlineSM: 12, headerBg: '#f8fafc', headerColor: '#475467', borderColor: '#eaecf0' },
      Tabs: { horizontalItemGutter: 24, titleFontSize: 14 },
      Descriptions: { itemPaddingBottom: 16, labelBg: '#f8fafc' },
    } }}
    modal={{ className: 'modeling-modal', classNames: {
      content: 'modeling-modal-content', header: 'modeling-modal-header',
      body: 'modeling-modal-body', footer: 'modeling-modal-footer',
    } }}
    drawer={{ classNames: {
      wrapper: 'modeling-drawer-wrapper', content: 'modeling-drawer-content',
      header: 'modeling-drawer-header', body: 'modeling-drawer-body', footer: 'modeling-drawer-footer',
    } }}>
    <div className="modeling-surface">{children ?? <Outlet />}</div>
  </ConfigProvider>;
}
