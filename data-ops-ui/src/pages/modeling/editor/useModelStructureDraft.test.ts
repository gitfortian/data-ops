import { act, renderHook } from "@testing-library/react";
import { useModelStructureDraft } from "./useModelStructureDraft";

test("late transformations cannot replace a draft edited after submission", () => {
  const hook = renderHook(() => useModelStructureDraft());
  act(() => hook.result.current.setTableName("orders"));
  const submitted = hook.result.current.captureDraft();
  expect(submitted()).toBe(true);
  act(() => hook.result.current.setTableComment("new user edit"));
  expect(submitted()).toBe(false);
  expect(hook.result.current.tableName).toBe("orders");
  expect(hook.result.current.tableComment).toBe("new user edit");
  const next = hook.result.current.captureDraft();
  act(() => hook.result.current.resetDraft());
  expect(next()).toBe(false);
  expect(hook.result.current.tableName).toBe("");
  expect(hook.result.current.dirty).toBe(false);
});
