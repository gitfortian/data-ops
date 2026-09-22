export const DATA_CATALOG_TREE_STYLES = `
  .catalog-tree.ant-tree {
    color: #344054;
    font-size: 14px;
  }
  .catalog-tree .ant-tree-list-holder-inner {
    gap: 2px;
  }
  .catalog-tree .ant-tree-treenode {
    box-sizing: border-box;
    width: 100%;
    min-height: 32px;
    padding: 0 8px !important;
    align-items: center;
    border-radius: 0;
    transition: background-color 0.15s ease;
  }
  .catalog-tree .ant-tree-treenode:hover {
    background: rgba(22, 24, 35, 0.035);
  }
  .catalog-tree .ant-tree-treenode:has(.ant-tree-node-selected) {
    background: rgba(22, 24, 35, 0.06);
  }
  .catalog-tree .ant-tree-node-content-wrapper {
    display: flex;
    min-width: 0;
    height: 32px;
    flex: 1;
    align-items: center;
    padding: 0 !important;
    border-radius: 0 !important;
    background: transparent !important;
    line-height: 32px;
  }
  .catalog-tree .ant-tree-node-content-wrapper.ant-tree-node-selected {
    color: #161823;
    background: transparent !important;
  }
  .catalog-tree .ant-tree-title {
    display: flex;
    min-width: 0;
    flex: 1;
  }
  .catalog-tree .ant-tree-indent-unit {
    width: 22px;
  }
  .catalog-tree .ant-tree-switcher {
    display: inline-flex;
    width: 20px;
    height: 32px;
    flex: none;
    align-items: center;
    justify-content: center;
    color: #8a8f99;
    line-height: 32px;
  }
  .catalog-tree .ant-tree-switcher svg {
    transition: transform 0.15s ease;
  }
  .catalog-tree .ant-tree-switcher_close svg {
    transform: rotate(-90deg);
  }
  .catalog-tree .ant-tree-switcher-noop {
    width: 20px;
  }
`;
