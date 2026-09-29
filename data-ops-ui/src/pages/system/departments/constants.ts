export const DEPARTMENT_IMPORT_EXAMPLE = JSON.stringify(
  [
    {
      deptName: '技术中心',
      description: '负责产品研发和技术平台建设',
      childDeptDTOList: [
        {
          deptName: '前端研发部',
          description: '负责 Web 与移动端研发',
        },
        {
          deptName: '后端研发部',
          description: '负责服务端与基础架构研发',
        },
      ],
    },
  ],
  null,
  2,
);
