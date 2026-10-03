import { act, renderHook } from "@testing-library/react";
import { useLatestOperation } from "./useLatestOperation";

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
