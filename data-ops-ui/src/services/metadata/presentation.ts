/** Backend LocalDateTime projection formatting, shared by metadata consumers. */
export const formatMetadataTime = (value?: string | null): string =>
  value ? String(value).replace("T", " ").slice(0, 19) : "-";
