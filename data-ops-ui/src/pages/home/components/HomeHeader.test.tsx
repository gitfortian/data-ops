import { render, screen } from "@testing-library/react";
import { HomeHeader } from "./HomeHeader";

jest.mock("@umijs/max", () => ({
  history: { push: jest.fn() },
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));

test("shows real zero but does not present a partial running count as a complete observation", () => {
  render(
    <HomeHeader
      stats={{
        dataSourceCount: 0,
        runningCount: 7,
        dataSourceAvailable: true,
        runningAvailable: false,
      }}
    />
  );
  expect(screen.getByText("0")).toBeInTheDocument();
  expect(screen.queryByText("7")).toBeNull();
  expect(screen.getByText("--")).toBeInTheDocument();
  expect(screen.getByRole("status")).toHaveTextContent("部分统计暂不可用");
});
