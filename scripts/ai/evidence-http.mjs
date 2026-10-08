// Bounded JSON reads shared by local evaluation consumers; never retain raw HTTP errors.
export async function readApiData(response) {
  if (!response.ok) {
    await response.body?.cancel().catch(() => {});
    throw new Error(`HTTP_${response.status}`);
  }
  const reader = response.body?.getReader();
  if (!reader) throw new Error('EMPTY_RESPONSE');
  const decoder = new TextDecoder();
  let bytes = 0, value = '';
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      bytes += chunk.value.byteLength;
      if (bytes > 2_000_000) throw new Error('RESPONSE_TOO_LARGE');
      value += decoder.decode(chunk.value, { stream: true });
    }
    value += decoder.decode();
    const body = JSON.parse(value);
    if (!body || body.success === false || body.code != null && ![0, '0', 200, '200'].includes(body.code)) throw new Error('API_REJECTED');
    return body.bizData ?? body.data;
  } finally { await reader.cancel().catch(() => {}); }
}
