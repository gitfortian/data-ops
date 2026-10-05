import HttpUtils from '@/utils/HttpUtils';

import type {
  ApprovalDetail,
  ApprovalFlow,
  ApprovalHandledRow,
  ApprovalInstance,
  ApprovalPage,
  ApprovalTodoRow,
  FlowPayload,
} from './types';

const P = '/api/v1/approvals';

const pageQuery = (pageNo: number, pageSize: number, extra?: Record<string, string | undefined>) => {
  const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
  Object.entries(extra ?? {}).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });
  return params.toString();
};

export const pageTodo = (pageNo: number, pageSize: number): Promise<ApprovalPage<ApprovalTodoRow>> =>
  HttpUtils.getData(`${P}/todo?${pageQuery(pageNo, pageSize)}`);

export const getTodoCount = (): Promise<number> => HttpUtils.getData(`${P}/todo/count`);

export const pageMine = (
  pageNo: number,
  pageSize: number,
  status?: string,
): Promise<ApprovalPage<ApprovalInstance>> =>
  HttpUtils.getData(`${P}/mine?${pageQuery(pageNo, pageSize, { status })}`);

export const pageHandled = (
  pageNo: number,
  pageSize: number,
): Promise<ApprovalPage<ApprovalHandledRow>> =>
  HttpUtils.getData(`${P}/handled?${pageQuery(pageNo, pageSize)}`);

export const getApprovalDetail = (id: number): Promise<ApprovalDetail> =>
  HttpUtils.getData(`${P}/${id}`);

export const approveInstance = (id: number, comment?: string): Promise<ApprovalInstance> =>
  HttpUtils.postData(`${P}/${id}/approve`, { comment });

export const rejectInstance = (id: number, comment: string): Promise<ApprovalInstance> =>
  HttpUtils.postData(`${P}/${id}/reject`, { comment });

export const cancelInstance = (id: number, reason?: string): Promise<boolean> =>
  HttpUtils.postData(`${P}/${id}/cancel`, { reason });

/** 在途优先,否则最近一单;从未发起返回 null(后端 data=null)。 */
export const findByBiz = (
  flowCode: string,
  bizType: string,
  bizId: string | number,
): Promise<ApprovalInstance | null> =>
  HttpUtils.getData(
    `${P}/by-biz?${new URLSearchParams({ flowCode, bizType, bizId: String(bizId) })}`,
  );

export const listFlows = (
  keyword?: string,
  pageNo = 1,
  pageSize = 50,
): Promise<ApprovalPage<ApprovalFlow>> =>
  HttpUtils.getData(`${P}/flows?${pageQuery(pageNo, pageSize, { keyword })}`);

export const createFlow = (payload: FlowPayload): Promise<ApprovalFlow> =>
  HttpUtils.postData(`${P}/flows`, payload);

/** flowCode 不可变,编辑只传名称/描述/级次。 */
export const updateFlow = (id: number, payload: FlowPayload): Promise<ApprovalFlow> =>
  HttpUtils.putData(`${P}/flows/${id}`, payload);

export const toggleFlow = (id: number): Promise<ApprovalFlow> =>
  HttpUtils.putData(`${P}/flows/${id}/toggle`);

export const deleteFlow = (id: number): Promise<boolean> =>
  HttpUtils.deleteData(`${P}/flows/${id}`);
