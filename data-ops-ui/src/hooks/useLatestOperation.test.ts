import { act, renderHook } from "@testing-library/react";
import { useLatestOperation, useResourceScope } from "./useLatestOperation";

test("independent operations share a resource but old callbacks cannot adopt a new resource", () => {
  const hook = renderHook(({ id }) => useResourceScope(id), {
    initialProps: { id: "project-a:model-1" },
  });
  const oldCapture = hook.result.current;
  const first = oldCapture();
  const second = oldCapture();
  expect(first()).toBe(true);
  expect(second()).toBe(true);
  hook.rerender({ id: "project-b:model-1" });
  expect(first()).toBe(false);
  expect(oldCapture()()).toBe(false);
  const current = hook.result.current();
  expect(current()).toBe(true);
  hook.rerender({ id: "project-a:model-1" });
  expect(first()).toBe(false);
  hook.unmount();
  expect(current()).toBe(false);
});

test("discards a response after another request, resource switch or unmount", () => {
  const hook = renderHook(({ id }) => useLatestOperation(id), {
    initialProps: { id: "first" },
  });
  const first = hook.result.current();
  const second = hook.result.current();
  expect(first()).toBe(false);
  expect(second()).toBe(true);
  hook.rerender({ id: "second" });
  expect(second()).toBe(false);
  hook.rerender({ id: "first" });
  expect(second()).toBe(false);
  let third!: () => boolean;
  act(() => {
    third = hook.result.current();
  });
  expect(third()).toBe(true);
  hook.unmount();
  expect(third()).toBe(false);
});
