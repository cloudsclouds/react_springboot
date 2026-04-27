// @ts-nocheck
const JAVA_API_BASE_URL = process.env.REACT_APP_JAVA_API_BASE_URL || 'http://localhost:8080/api';

export interface ApiResult<T> {
  ok: boolean;
  status: number;
  data: T;
}

export async function postJson<TResponse>(path: string, payload: unknown): Promise<ApiResult<TResponse>> {
  const response = await fetch(`${JAVA_API_BASE_URL}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  const data = (await response.json()) as TResponse;

  return {
    ok: response.ok,
    status: response.status,
    data,
  };
}