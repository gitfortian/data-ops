import {
  buildPositionMappings,
  buildSameNameMappings,
  rowsToMappingValue,
} from "./fieldMappingModel";

test("matches names case insensitively and preserves source order and target spelling", () => {
  const source = [
    { value: "ID", label: "ID" },
    { value: "missing", label: "missing" },
    { value: "NAME", label: "NAME" },
  ];
  const target = [
    { value: "name", label: "name" },
    { value: "id", label: "id" },
  ];
  const result = buildSameNameMappings(
    source,
    target,
    (index) => `row-${index}`
  );
  expect(rowsToMappingValue(result)).toEqual([
    { source: "ID", target: "id" },
    { source: "NAME", target: "name" },
  ]);
  expect(result.map((row) => row.key)).toEqual(["row-0", "row-2"]);
});

test("position mapping stops at the smaller side and does not persist incomplete rows", () => {
  const result = buildPositionMappings(
    [
      { value: "a", label: "a" },
      { value: "b", label: "b" },
    ],
    [{ value: "target", label: "target" }],
    (index) => `row-${index}`
  );
  expect(
    rowsToMappingValue([...result, { key: "draft", sourceField: "b" }])
  ).toEqual([{ source: "a", target: "target" }]);
});
