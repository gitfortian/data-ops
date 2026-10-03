export const dataServiceDetailUrl = (serviceId: number | string) =>
  `/data-service/api/${encodeURIComponent(String(serviceId))}`;
