import { readClarificationQuestion } from './clarification';
import { readContinuation } from './continuation';

export const fieldQuestion = {
  question: '统计实付还是应付？', options: ['实付金额（fieldId=paid）', '应付金额（fieldId=due）'],
  queryContext: { kind: 'FIELD', datasetId: 9, versionNo: 3, truncated: true, fields: [
    { fieldId: 'paid', displayName: '实付金额', dataType: 'DECIMAL', role: 'MEASURE', description: '源业务定义' },
    { fieldId: 'due', displayName: '应付金额', dataType: 'DECIMAL', role: 'MEASURE', description: '未扣优惠' },
  ] },
};

it.each(['FIELD', 'TIME', 'CALIBER'])('reads bounded %s source in live and restored questions', kind => {
  const question = JSON.stringify({ ...fieldQuestion, queryContext: { ...fieldQuestion.queryContext, kind } });
  expect(readClarificationQuestion(question).queryContext?.kind).toBe(kind);
  const clarification = { toolCallId: 'c1', toolName: 'request_clarification', question };
  expect(readContinuation({ sessionId: 's1', turnId: 't1', status: 'WAITING_INPUT', clarification }, 's1').clarification).toEqual(clarification);
});

it('keeps legacy plain and structured questions without inventing field evidence', () => {
  expect(readClarificationQuestion('哪个分区？')).toEqual({ question: '哪个分区？', options: [] });
  expect(readClarificationQuestion('{"question":"哪个分区？","options":["昨天"]}')).toEqual({ question: '哪个分区？', options: ['昨天'] });
});

it.each([
  { queryContext: { ...fieldQuestion.queryContext, versionNo: 0 } },
  { queryContext: { ...fieldQuestion.queryContext, kind: 'SQL' } },
  { queryContext: { ...fieldQuestion.queryContext, fields: [fieldQuestion.queryContext.fields[0]] } },
  { queryContext: { ...fieldQuestion.queryContext, fields: [fieldQuestion.queryContext.fields[0], fieldQuestion.queryContext.fields[0]] } },
  { queryContext: { ...fieldQuestion.queryContext, truncated: undefined } },
  { options: ['伪造金额', '应付金额（fieldId=due）'] },
  { options: [{}] }, { options: ['a', 'a'] }, { options: Array(9).fill('a') },
  { question: 'x'.repeat(2049) },
])('rejects malformed source and options %j', replacement => {
  expect(() => readClarificationQuestion(JSON.stringify({ ...fieldQuestion, ...replacement }))).toThrow();
});

it.each(['{broken', '[]', 'x'.repeat(12001), ''])('rejects corrupt/oversized payload', question => {
  expect(() => readClarificationQuestion(question)).toThrow();
});
